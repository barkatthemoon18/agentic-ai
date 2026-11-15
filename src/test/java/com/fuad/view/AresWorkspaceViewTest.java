package com.fuad.view;

import com.fuad.media.control.MediaActionHandler;
import com.fuad.presentation.JavaFxRuntime;
import com.fuad.presentation.core.WorkspaceType;
import com.fuad.presentation.media.MediaWorkspaceSnapshot;
import com.fuad.presentation.tools.MoreToolsHandler;
import com.fuad.presentation.tools.ToolsActionHandler;
import com.fuad.presentation.tools.ToolsWorkspaceConfig;
import com.fuad.terminal.PowershellSession;
import com.fuad.view.workspace.media.MediaWorkspaceView;
import com.fuad.view.workspace.tools.TerminalWorkspaceView;
import com.fuad.view.workspace.tools.ToolsWorkspaceView;
import com.fuad.view.workspace.web.ResearchLifecycleListener;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AresWorkspaceViewTest {

    private static JavaFxRuntime runtime;

    @BeforeAll
    static void initToolkit() {
        runtime = new JavaFxRuntime();
    }

    @Test
    void shouldRejectNullRequiredArguments() {
        ResearchLifecycleListener listener = ResearchLifecycleListener.noop();
        ToolsActionHandler toolsActionHandler = mock(ToolsActionHandler.class);
        MoreToolsHandler moreToolsHandler = mock(MoreToolsHandler.class);
        ToolsWorkspaceConfig config = ToolsWorkspaceConfig.empty();
        MediaActionHandler mediaActionHandler = mock(MediaActionHandler.class);
        PowershellSession terminalSession = mock(PowershellSession.class);

        assertThrows(NullPointerException.class, () ->
                new AresWorkspaceView(listener, null, moreToolsHandler, config, MediaWorkspaceSnapshot::unavailable, mediaActionHandler, terminalSession));
        assertThrows(NullPointerException.class, () ->
                new AresWorkspaceView(listener, toolsActionHandler, null, config, MediaWorkspaceSnapshot::unavailable, mediaActionHandler, terminalSession));
        assertThrows(NullPointerException.class, () ->
                new AresWorkspaceView(listener, toolsActionHandler, moreToolsHandler, null, MediaWorkspaceSnapshot::unavailable, mediaActionHandler, terminalSession));
        assertThrows(NullPointerException.class, () ->
                new AresWorkspaceView(listener, toolsActionHandler, moreToolsHandler, config, null, mediaActionHandler, terminalSession));
        assertThrows(NullPointerException.class, () ->
                new AresWorkspaceView(listener, toolsActionHandler, moreToolsHandler, config, MediaWorkspaceSnapshot::unavailable, null, terminalSession));
        assertThrows(NullPointerException.class, () ->
                new AresWorkspaceView(listener, toolsActionHandler, moreToolsHandler, config, MediaWorkspaceSnapshot::unavailable, mediaActionHandler, null));
    }

    @Test
    void defaultConstructorShouldShowToolsWorkspaceAndPropagateTerminalSession() {
        PowershellSession session = mock(PowershellSession.class);
        when(session.process()).thenReturn(Optional.empty());
        when(session.error()).thenReturn(Optional.of("offline"));

        AresWorkspaceView[] viewHolder = new AresWorkspaceView[1];
        runtime.runAndWait(() -> viewHolder[0] = new AresWorkspaceView(
                ResearchLifecycleListener.noop(),
                mock(ToolsActionHandler.class),
                mock(MoreToolsHandler.class),
                ToolsWorkspaceConfig.empty(),
                MediaWorkspaceSnapshot::unavailable,
                mock(MediaActionHandler.class),
                session
        ));

        AresWorkspaceView view = viewHolder[0];
        assertNotNull(view);

        StackPane contentHost = view.getChildren().stream()
                .filter(node -> node instanceof StackPane && node.getStyleClass().contains("workspace-content"))
                .map(node -> (StackPane) node)
                .findFirst()
                .orElse(null);

        assertNotNull(contentHost);
        assertEquals(1, contentHost.getChildren().size());
        assertInstanceOf(ToolsWorkspaceView.class, contentHost.getChildren().getFirst());

        ToolsWorkspaceView toolsView = (ToolsWorkspaceView) contentHost.getChildren().getFirst();
        boolean hasTerminal = toolsView.getChildren().stream().anyMatch(node -> node instanceof TerminalWorkspaceView);
        assertTrue(hasTerminal, "ToolsWorkspaceView must contain TerminalWorkspaceView");
    }

    @Test
    void switchingWorkspacesShouldUpdateSubtitleAndContent() {
        PowershellSession session = mock(PowershellSession.class);
        when(session.process()).thenReturn(Optional.empty());

        AresWorkspaceView[] viewHolder = new AresWorkspaceView[1];
        runtime.runAndWait(() -> viewHolder[0] = new AresWorkspaceView(
                ResearchLifecycleListener.noop(),
                mock(ToolsActionHandler.class),
                mock(MoreToolsHandler.class),
                ToolsWorkspaceConfig.empty(),
                MediaWorkspaceSnapshot::unavailable,
                mock(MediaActionHandler.class),
                session
        ));

        AresWorkspaceView view = viewHolder[0];
        StackPane contentHost = (StackPane) view.getChildren().stream()
                .filter(node -> node instanceof StackPane && node.getStyleClass().contains("workspace-content"))
                .findFirst()
                .orElseThrow();

        Label subtitleLabel = view.getChildren().stream()
                .filter(node -> node instanceof Label && node.getStyleClass().contains("workspace-subtitle"))
                .map(node -> (Label) node)
                .findFirst()
                .orElseThrow();

        assertEquals("WORKSPACE // " + WorkspaceType.TOOLS.subtitle().toUpperCase(), subtitleLabel.getText());

        runtime.runAndWait(() -> view.showWorkspace(WorkspaceType.MEDIA));
        assertEquals("WORKSPACE // " + WorkspaceType.MEDIA.subtitle().toUpperCase(), subtitleLabel.getText());
        assertInstanceOf(MediaWorkspaceView.class, contentHost.getChildren().getFirst());

        runtime.runAndWait(() -> view.showWorkspace(WorkspaceType.TOOLS));
        assertEquals("WORKSPACE // " + WorkspaceType.TOOLS.subtitle().toUpperCase(), subtitleLabel.getText());
        assertInstanceOf(ToolsWorkspaceView.class, contentHost.getChildren().getFirst());
    }

    @Test
    void disposeShouldSafelyCleanUpInstantiatedToolsAndMediaWorkspaces() {
        PowershellSession session = mock(PowershellSession.class);
        when(session.process()).thenReturn(Optional.empty());

        AresWorkspaceView[] viewHolder = new AresWorkspaceView[1];
        runtime.runAndWait(() -> viewHolder[0] = new AresWorkspaceView(
                ResearchLifecycleListener.noop(),
                mock(ToolsActionHandler.class),
                mock(MoreToolsHandler.class),
                ToolsWorkspaceConfig.empty(),
                MediaWorkspaceSnapshot::unavailable,
                mock(MediaActionHandler.class),
                session
        ));

        AresWorkspaceView view = viewHolder[0];

        // Instantiate both TOOLS and MEDIA workspaces
        runtime.runAndWait(() -> {
            view.showWorkspace(WorkspaceType.TOOLS);
            view.showWorkspace(WorkspaceType.MEDIA);
        });

        // Dispose should clean up without error and be idempotent
        assertDoesNotThrow(() -> runtime.runAndWait(view::dispose));
        assertDoesNotThrow(() -> runtime.runAndWait(view::dispose));
    }
}

package com.fuad.view.workspace.tools;

import com.fuad.pipeline.OsAction;
import com.fuad.presentation.JavaFxRuntime;
import com.fuad.presentation.tools.*;
import com.fuad.terminal.PowershellSession;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ToolsWorkspaceViewTest {

    private static JavaFxRuntime runtime;

    @BeforeAll
    static void initToolkit() {
        runtime = new JavaFxRuntime();
    }

    @Test
    void shouldRejectNullArguments() {
        ToolsWorkspaceConfig config = ToolsWorkspaceConfig.empty();
        ToolsActionHandler actionHandler = mock(ToolsActionHandler.class);
        MoreToolsHandler moreToolsHandler = mock(MoreToolsHandler.class);
        PowershellSession terminalSession = mock(PowershellSession.class);

        assertThrows(NullPointerException.class, () -> new ToolsWorkspaceView(null, actionHandler, moreToolsHandler, terminalSession));
        assertThrows(NullPointerException.class, () -> new ToolsWorkspaceView(config, null, moreToolsHandler, terminalSession));
        assertThrows(NullPointerException.class, () -> new ToolsWorkspaceView(config, actionHandler, null, terminalSession));
        assertThrows(NullPointerException.class, () -> new ToolsWorkspaceView(config, actionHandler, moreToolsHandler, null));
    }

    @Test
    void shouldEmbedTerminalInLayoutWithPriorityAlways() {
        PowershellSession session = mock(PowershellSession.class);
        when(session.process()).thenReturn(Optional.empty());
        when(session.error()).thenReturn(Optional.of("offline"));

        ToolsWorkspaceView[] viewHolder = new ToolsWorkspaceView[1];
        runtime.runAndWait(() -> viewHolder[0] = new ToolsWorkspaceView(ToolsWorkspaceConfig.empty(),
                mock(ToolsActionHandler.class), mock(MoreToolsHandler.class), session));

        ToolsWorkspaceView view = viewHolder[0];
        TerminalWorkspaceView terminalView = view.getChildren().stream()
                .filter(node -> node instanceof TerminalWorkspaceView)
                .map(node -> (TerminalWorkspaceView) node)
                .findFirst()
                .orElse(null);

        assertNotNull(terminalView);
        assertEquals(Priority.ALWAYS, VBox.getVgrow(terminalView));
    }

    @Test
    void clickingPowershellToolShouldFallbackToActionHandlerWhenTerminalCannotFocus() {
        PowershellSession session = mock(PowershellSession.class);
        when(session.process()).thenReturn(Optional.empty());
        when(session.error()).thenReturn(Optional.of("unavailable"));

        ToolsActionHandler actionHandler = mock(ToolsActionHandler.class);
        when(actionHandler.submit(any())).thenReturn(true);
        MoreToolsHandler moreHandler = mock(MoreToolsHandler.class);

        ToolApplication pwshApp = new ToolApplication("powershell", "PowerShell", "powershell.exe");
        ToolsWorkspaceConfig config = new ToolsWorkspaceConfig(1, "TOOLS", 6, List.of("powershell"), List.of(pwshApp));

        ToolsWorkspaceView[] viewHolder = new ToolsWorkspaceView[1];
        runtime.runAndWait(() -> viewHolder[0] = new ToolsWorkspaceView(config, actionHandler, moreHandler, session));

        ToolsWorkspaceView view = viewHolder[0];
        GridPane grid = (GridPane) view.getChildren().getFirst();
        Button pwshButton = grid.getChildren().stream()
                .filter(node -> node instanceof Button)
                .map(node -> (Button) node)
                .filter(b -> containsText(b, "PowerShell"))
                .findFirst()
                .orElse(null);

        assertNotNull(pwshButton);
        runtime.runAndWait(pwshButton::fire);

        verify(actionHandler).submit(argThat(request ->
                request.action() == OsAction.OPEN_APPLICATION && "powershell.exe".equals(request.target())));
    }

    @Test
    void disposeShouldSafelyDisposeTerminalView() {
        PowershellSession session = mock(PowershellSession.class);
        when(session.process()).thenReturn(Optional.empty());

        ToolsWorkspaceView[] viewHolder = new ToolsWorkspaceView[1];
        runtime.runAndWait(() -> viewHolder[0] = new ToolsWorkspaceView(ToolsWorkspaceConfig.empty(),
                mock(ToolsActionHandler.class), mock(MoreToolsHandler.class), session));

        ToolsWorkspaceView view = viewHolder[0];
        assertDoesNotThrow(view::dispose);
    }

    private static boolean containsText(Node node, String text) {
        if (node instanceof Button button) {
            if (button.getText() != null && button.getText().contains(text)) {
                return true;
            }
            if (button.getGraphic() != null && containsText(button.getGraphic(), text)) {
                return true;
            }
        }
        if (node instanceof Label label) {
            return label.getText() != null && label.getText().contains(text);
        }
        if (node instanceof javafx.scene.Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                if (containsText(child, text)) {
                    return true;
                }
            }
        }
        return false;
    }
}

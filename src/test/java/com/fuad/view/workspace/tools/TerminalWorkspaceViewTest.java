package com.fuad.view.workspace.tools;

import com.pty4j.PtyProcess;
import com.fuad.presentation.JavaFxRuntime;
import com.fuad.terminal.PowershellSession;
import javafx.scene.Node;
import javafx.scene.control.Label;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TerminalWorkspaceViewTest {

    private static JavaFxRuntime runtime;

    @BeforeAll
    static void initToolkit() {
        runtime = new JavaFxRuntime();
    }

    @Test
    void shouldRejectNullSession() {
        assertThrows(NullPointerException.class, () -> new TerminalWorkspaceView(null));
    }

    @Test
    void shouldShowUnavailableWhenProcessIsNotStarted() {
        PowershellSession session = mock(PowershellSession.class);
        when(session.process()).thenReturn(Optional.empty());
        when(session.error()).thenReturn(Optional.of("PowerShell terminal requires Windows"));

        TerminalWorkspaceView[] viewHolder = new TerminalWorkspaceView[1];
        runtime.runAndWait(() -> viewHolder[0] = new TerminalWorkspaceView(session));

        TerminalWorkspaceView view = viewHolder[0];
        assertNotNull(view);
        assertFalse(view.focusInput());

        Label errorLabel = view.getChildren().stream()
                .filter(node -> node.getStyleClass().contains("ares-terminal-error"))
                .map(node -> (Label) node)
                .findFirst()
                .orElse(null);

        assertNotNull(errorLabel);
        assertEquals("PowerShell terminal requires Windows", errorLabel.getText());

        Label statusLabel = findLabelByStyleClass(view, "ares-terminal-status");
        assertNotNull(statusLabel);
        assertEquals("UNAVAILABLE", statusLabel.getText());
    }

    @Test
    void shouldShowUnavailableWhenProcessIsTerminated() {
        PowershellSession session = mock(PowershellSession.class);
        PtyProcess deadProcess = mock(PtyProcess.class);
        when(deadProcess.isAlive()).thenReturn(false);
        when(session.process()).thenReturn(Optional.of(deadProcess));

        TerminalWorkspaceView[] viewHolder = new TerminalWorkspaceView[1];
        runtime.runAndWait(() -> viewHolder[0] = new TerminalWorkspaceView(session));

        TerminalWorkspaceView view = viewHolder[0];
        assertFalse(view.focusInput());

        Label errorLabel = view.getChildren().stream()
                .filter(node -> node.getStyleClass().contains("ares-terminal-error"))
                .map(node -> (Label) node)
                .findFirst()
                .orElse(null);

        assertNotNull(errorLabel);
        assertEquals("PowerShell session terminated", errorLabel.getText());

        Label statusLabel = findLabelByStyleClass(view, "ares-terminal-status");
        assertNotNull(statusLabel);
        assertEquals("UNAVAILABLE", statusLabel.getText());
    }

    @Test
    void disposeShouldBeSafeWhenUnavailableOrMultipleTimes() {
        PowershellSession session = mock(PowershellSession.class);
        when(session.process()).thenReturn(Optional.empty());
        when(session.error()).thenReturn(Optional.of("offline"));

        TerminalWorkspaceView[] viewHolder = new TerminalWorkspaceView[1];
        runtime.runAndWait(() -> viewHolder[0] = new TerminalWorkspaceView(session));

        TerminalWorkspaceView view = viewHolder[0];
        assertDoesNotThrow(view::dispose);
        assertDoesNotThrow(view::dispose);
        assertFalse(view.focusInput());
    }

    private static Label findLabelByStyleClass(Node root, String styleClass) {
        if (root instanceof Label label && label.getStyleClass().contains(styleClass)) {
            return label;
        }
        if (root instanceof javafx.scene.Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                Label found = findLabelByStyleClass(child, styleClass);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}

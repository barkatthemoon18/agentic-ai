package com.fuad.view;

import com.fuad.media.control.MediaActionHandler;
import com.fuad.pipeline.VoiceInputController;
import com.fuad.pipeline.VoiceSignalSnapshot;
import com.fuad.presentation.JavaFxRuntime;
import com.fuad.presentation.media.MediaWorkspaceSnapshot;
import com.fuad.presentation.tools.MoreToolsHandler;
import com.fuad.presentation.tools.ToolsActionHandler;
import com.fuad.presentation.tools.ToolsWorkspaceConfig;
import com.fuad.terminal.PowershellSession;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CoreDashboardVoiceControlsTest {
    private static JavaFxRuntime runtime;

    @BeforeAll
    static void initToolkit() {
        runtime = new JavaFxRuntime();
    }

    @Test
    void externalMuteChangesUpdateTheDashboardAndItsButtonStillControlsTheSameState() {
        VoiceInputController controller = new VoiceInputController();
        controller.setMuted(true);
        CoreDashboardView[] view = new CoreDashboardView[1];
        runtime.runAndWait(() -> {
            view[0] = dashboard(controller);
            assertVoiceControls(view[0], true);
        });
        try {
            // Simulate the keyboard overlay or another controller changing voice state off the FX thread.
            controller.setMuted(false);
            runtime.runAndWait(() -> assertVoiceControls(view[0], false));

            controller.setMuted(true);
            runtime.runAndWait(() -> {
                assertVoiceControls(view[0], true);
                muteButton(view[0]).fire();
                assertFalse(controller.isMuted());
                assertVoiceControls(view[0], false);
            });
        } finally {
            runtime.runAndWait(view[0]::dispose);
        }
    }

    @Test
    void disposedDashboardStopsFollowingExternalVoiceChangesAndCanBeDisposedTwice() {
        VoiceInputController controller = new VoiceInputController();
        controller.setMuted(true);
        CoreDashboardView[] view = new CoreDashboardView[1];
        runtime.runAndWait(() -> {
            view[0] = dashboard(controller);
            assertVoiceControls(view[0], true);
            assertDoesNotThrow(view[0]::dispose);
            assertDoesNotThrow(view[0]::dispose);
        });

        controller.setMuted(false);
        runtime.runAndWait(() -> {
            assertFalse(controller.isMuted());
            assertVoiceControls(view[0], true);
        });
    }

    private static CoreDashboardView dashboard(VoiceInputController controller) {
        PowershellSession session = mock(PowershellSession.class);
        when(session.process()).thenReturn(Optional.empty());
        when(session.error()).thenReturn(Optional.of("offline"));
        return new CoreDashboardView(VoiceSignalSnapshot::silence, controller,
                ToolsActionHandler.unavailable(), MoreToolsHandler.unavailable(), ToolsWorkspaceConfig.empty(),
                MediaWorkspaceSnapshot::unavailable, MediaActionHandler.unavailable(), session);
    }

    private static ToggleButton muteButton(CoreDashboardView view) {
        return assertInstanceOf(ToggleButton.class, view.lookup(".core-footer-mute"));
    }

    private static void assertVoiceControls(CoreDashboardView view, boolean muted) {
        ToggleButton mute = muteButton(view);
        assertEquals(muted, mute.isSelected());
        assertEquals(muted ? "MUTE // ON" : "MUTE", mute.getText());
        Label modality = assertInstanceOf(Label.class, view.lookup(".core-footer-status"));
        assertEquals(muted ? "● TOUCH ONLY" : "● VOICE + TOUCH", modality.getText());
    }
}

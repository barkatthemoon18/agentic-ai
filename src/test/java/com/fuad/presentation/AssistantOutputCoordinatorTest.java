package com.fuad.presentation;

import com.fuad.audio.AssistantAudioController;
import com.fuad.audio.AudioDeviceInfo;
import com.fuad.audio.AudioPlaybackService;
import com.fuad.pipeline.AudioPipeline;
import com.fuad.tts.TtsAudio;
import com.fuad.tts.TtsEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssistantOutputCoordinatorTest {
    private AssistantAudioController audioController;
    private TrackingAudioPipeline audioPipeline;
    private TrackingVisualOutput visualOutput;
    private AssistantOutputCoordinator coordinator;
    private final List<String> events = new ArrayList<>();

    @BeforeEach
    void setUp() {
        audioController = new AssistantAudioController();
        audioPipeline = new TrackingAudioPipeline(audioController);
        visualOutput = new TrackingVisualOutput();
        audioPipeline.events = events;
        visualOutput.events = events;
        coordinator = new AssistantOutputCoordinator(
                audioController,
                new OutputPresentationPolicy(20),
                audioPipeline,
                visualOutput);
    }

    @Test
    void shouldUseOnlyAudioAtNormalVolume() {
        audioController.setVolume(40);

        coordinator.present("Respuesta normal");

        assertEquals(1, audioPipeline.speakCalls);
        assertEquals("Respuesta normal", audioPipeline.spokenText);
        assertEquals(0, visualOutput.showCalls);
        assertEquals(1, visualOutput.hideCalls);
    }

    @Test
    void shouldUseAudioAndTextBelowThreshold() {
        audioController.setVolume(19);

        coordinator.present("Respuesta con volumen bajo");

        assertEquals(1, visualOutput.showCalls);
        assertNotNull(visualOutput.lastMessage);
        assertEquals("Respuesta con volumen bajo", visualOutput.lastMessage.getText());
        assertEquals(19, visualOutput.lastMessage.getAudioSnapshot().getVolume());
        assertEquals(1, audioPipeline.speakCalls);
        assertEquals("Respuesta con volumen bajo", audioPipeline.spokenText);
    }

    @Test
    void shouldUseTextOnlyWhenMuted() {
        audioController.setVolume(40);
        audioController.mute();

        coordinator.present("Respuesta silenciada");

        assertEquals(1, visualOutput.showCalls);
        assertTrue(visualOutput.lastMessage.getAudioSnapshot().isMuted());
        assertEquals(0, audioPipeline.speakCalls);
        assertEquals(0, visualOutput.hideCalls);
    }

    @Test
    void shouldTreatThresholdAsAudioOnly() {
        audioController.setVolume(20);

        coordinator.present("Volumen veinte");

        assertEquals(1, audioPipeline.speakCalls);
        assertEquals(0, visualOutput.showCalls);
        assertEquals(1, visualOutput.hideCalls);
    }

    @Test
    void shouldContinueWithAudioWhenVisualShowFailsAtLowVolume() {
        audioController.setVolume(10);
        visualOutput.failOnShow = true;

        assertDoesNotThrow(() -> coordinator.present("Respuesta"));

        assertEquals(1, visualOutput.showCalls);
        assertEquals(1, audioPipeline.speakCalls);
    }

    @Test
    void shouldNotProduceAudioWhenVisualShowFailsWhileMuted() {
        audioController.mute();
        visualOutput.failOnShow = true;

        assertDoesNotThrow(() -> coordinator.present("Respuesta"));

        assertEquals(1, visualOutput.showCalls);
        assertEquals(0, audioPipeline.speakCalls);
    }

    @Test
    void shouldContinueWithAudioWhenVisualHideFails() {
        audioController.setVolume(40);
        visualOutput.failOnHide = true;

        assertDoesNotThrow(() -> coordinator.present("Respuesta"));

        assertEquals(1, visualOutput.hideCalls);
        assertEquals(1, audioPipeline.speakCalls);
    }

    @Test
    void shouldRejectNullText() {
        assertThrows(NullPointerException.class, () -> coordinator.present(null));

        assertEquals(0, audioPipeline.speakCalls);
        assertEquals(0, visualOutput.showCalls);
        assertEquals(0, visualOutput.hideCalls);
    }

    @Test
    void shouldRejectBlankText() {
        assertThrows(IllegalArgumentException.class, () -> coordinator.present("   "));

        assertEquals(0, audioPipeline.speakCalls);
        assertEquals(0, visualOutput.showCalls);
        assertEquals(0, visualOutput.hideCalls);
    }

    @Test
    void shouldCloseVisualOutput() {
        assertDoesNotThrow(coordinator::close);

        assertEquals(1, visualOutput.closeCalls);
    }

    @Test
    void shouldContainVisualCloseFailure() {
        visualOutput.failOnClose = true;

        assertDoesNotThrow(coordinator::close);

        assertEquals(1, visualOutput.closeCalls);
    }

    @Test
    void shouldTransitionFromMutedThroughLowVolumeToAudioOnly() {
        audioController.setVolume(40);
        audioController.mute();
        coordinator.present("Silencio");
        audioController.setVolume(10);
        coordinator.present("Volumen bajo");
        audioController.setVolume(40);
        coordinator.present("Volumen normal");

        assertEquals(List.of("show", "show", "speak", "hide", "speak"), events);
        assertEquals(2, audioPipeline.speakCalls);
        assertEquals(2, visualOutput.showCalls);
    }

    @Test
    void shouldRestoreAudibleVolumeAfterZeroAndHidePreviousText() {
        audioController.setVolume(40);
        audioController.setVolume(0);
        coordinator.present("Cero");
        assertEquals(0, audioPipeline.speakCalls);
        assertEquals(0, visualOutput.lastMessage.getAudioSnapshot().getVolume());

        audioController.unmute();
        coordinator.present("Recuperado");

        assertEquals(40, audioController.getVolume());
        assertEquals(List.of("show", "hide", "speak"), events);
    }

    private static final class TrackingVisualOutput implements VisualOutput {
        private List<String> events;
        private int showCalls;
        private int hideCalls;
        private int closeCalls;
        private VisualMessage lastMessage;
        private boolean failOnShow;
        private boolean failOnHide;
        private boolean failOnClose;

        @Override
        public void show(VisualMessage message) {
            events.add("show");
            showCalls++;
            if (failOnShow) {
                throw new IllegalStateException("show failure");
            }
            lastMessage = message;
        }

        @Override
        public void hide() {
            events.add("hide");
            hideCalls++;
            if (failOnHide) {
                throw new IllegalStateException("hide failure");
            }
        }

        @Override
        public void close() {
            closeCalls++;
            if (failOnClose) {
                throw new IllegalStateException("close failure");
            }
        }
    }

    private static final class TrackingAudioPipeline extends AudioPipeline {
        private List<String> events;
        private int speakCalls;
        private String spokenText;

        private TrackingAudioPipeline(AssistantAudioController audioController) {
            super(
                    new UnusedTtsEngine(),
                    new AudioPlaybackService(),
                    new AudioDeviceInfo(null, "test", "test", "test"),
                    audioController);
        }

        @Override
        public void speak(String text) {
            events.add("speak");
            speakCalls++;
            spokenText = text;
        }
    }

    private static final class UnusedTtsEngine implements TtsEngine {
        @Override
        public TtsAudio synthesize(String text) {
            throw new AssertionError("TTS engine should not be called directly");
        }

        @Override
        public void close() {
            // Nothing to close.
        }
    }
}

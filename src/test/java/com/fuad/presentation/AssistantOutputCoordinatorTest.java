package com.fuad.presentation;

import com.fuad.audio.AssistantAudioController;
import com.fuad.assistant.AssistantResult;
import com.fuad.assistant.skills.os.ApplicationCatalogPayload;
import com.fuad.assistant.skills.os.ApplicationListItem;
import com.fuad.assistant.skills.os.OpenApplicationItem;
import com.fuad.assistant.skills.os.OpenApplicationsPayload;
import com.fuad.audio.AudioDeviceInfo;
import com.fuad.audio.AudioPlaybackService;
import com.fuad.audio.output.MediaExclusiveAudioDetector;
import com.fuad.audio.output.AudioDeliveryState;
import com.fuad.pipeline.AudioPipeline;
import com.fuad.tts.TtsAudio;
import com.fuad.tts.TtsEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

class AssistantOutputCoordinatorTest {
    private AssistantAudioController audioController;
    private TrackingAudioPipeline audioPipeline;
    private TrackingVisualOutput visualOutput;
    private AssistantOutputCoordinator coordinator;
    private MediaExclusiveAudioDetector exclusiveAudioDetector;
    private final List<String> events = new ArrayList<>();

    @BeforeEach
    void setUp() {
        audioController = new AssistantAudioController();
        audioPipeline = new TrackingAudioPipeline(audioController);
        visualOutput = new TrackingVisualOutput();
        audioPipeline.events = events;
        visualOutput.events = events;
        exclusiveAudioDetector = mock(MediaExclusiveAudioDetector.class);
        coordinator = new AssistantOutputCoordinator(
                audioController,
                new OutputPresentationPolicy(20),
                audioPipeline,
                visualOutput, exclusiveAudioDetector);
    }

    @ParameterizedTest
    @MethodSource("reservedPayloadCases")
    void reservedPayloadShouldKeepVisualContentWithoutSpeech(AssistantResult result, int volume) {
        audioController.setVolume(volume);
        when(exclusiveAudioDetector.isOutputReserved()).thenReturn(true);
        var snapshot = audioController.getSnapshot();

        coordinator.present(result);

        assertEquals(0, audioPipeline.speakCalls);
        assertEquals(0, visualOutput.hideCalls);
        assertEquals(1, visualOutput.showCalls);
        assertSame(result.getPayload(), visualOutput.lastMessage.getPayload());
        assertEquals(result.getText(), visualOutput.lastMessage.getText());
        assertEquals(snapshot.getVolume(), visualOutput.lastMessage.getAudioSnapshot().getVolume());
        assertEquals(snapshot.isMuted(), visualOutput.lastMessage.getAudioSnapshot().isMuted());
        assertEquals(snapshot.getGain(), visualOutput.lastMessage.getAudioSnapshot().getGain());
        assertEquals(AudioDeliveryState.OUTPUT_RESERVED, visualOutput.lastMessage.getAudioDeliveryState());
    }

    @ParameterizedTest
    @MethodSource("silentPayloadCases")
    void silentPayloadShouldNotConsultReservation(AssistantResult result, int volume, boolean muted) {
        audioController.setVolume(volume);
        if (muted) audioController.mute();

        coordinator.present(result);

        verifyNoInteractions(exclusiveAudioDetector);
        assertEquals(0, audioPipeline.speakCalls);
        assertEquals(0, visualOutput.hideCalls);
        assertEquals(1, visualOutput.showCalls);
        assertSame(result.getPayload(), visualOutput.lastMessage.getPayload());
        assertEquals(AudioDeliveryState.NORMAL, visualOutput.lastMessage.getAudioDeliveryState());
    }

    @ParameterizedTest
    @MethodSource("applicationResults")
    void reservedPayloadShouldRemainSilentWhenVisualOutputFails(AssistantResult result) {
        audioController.setVolume(40);
        when(exclusiveAudioDetector.isOutputReserved()).thenReturn(true);
        visualOutput.failOnShow = true;

        assertDoesNotThrow(() -> coordinator.present(result));

        assertEquals(0, audioPipeline.speakCalls);
        assertEquals(0, visualOutput.hideCalls);
        assertEquals(1, visualOutput.showCalls);
    }

    @ParameterizedTest
    @MethodSource("applicationResults")
    void payloadSpeechShouldResumeAfterReservationEnds(AssistantResult result) {
        audioController.setVolume(40);
        when(exclusiveAudioDetector.isOutputReserved()).thenReturn(true, false);

        coordinator.present(result);
        assertEquals(0, audioPipeline.speakCalls);
        assertEquals(AudioDeliveryState.OUTPUT_RESERVED, visualOutput.lastMessage.getAudioDeliveryState());

        coordinator.present(result);

        assertEquals(1, audioPipeline.speakCalls);
        assertEquals(result.getText(), audioPipeline.spokenText);
        boolean forceVisual = result.getPayload() instanceof ApplicationCatalogPayload
                || ((OpenApplicationsPayload) result.getPayload()).items().size() > 5;
        assertEquals(forceVisual ? List.of("show", "show", "speak") : List.of("show", "hide", "speak"), events);
        if (forceVisual) {
            assertSame(result.getPayload(), visualOutput.lastMessage.getPayload());
            assertEquals(AudioDeliveryState.NORMAL, visualOutput.lastMessage.getAudioDeliveryState());
        }
    }

    private static Stream<Arguments> reservedPayloadCases() {
        return Stream.of(19, 20, 40).flatMap(volume -> applicationResults()
                .map(result -> Arguments.of(result, volume)));
    }

    private static Stream<Arguments> silentPayloadCases() {
        return Stream.concat(applicationResults().map(result -> Arguments.of(result, 0, false)),
                applicationResults().map(result -> Arguments.of(result, 40, true)));
    }

    private static Stream<AssistantResult> applicationResults() {
        ApplicationCatalogPayload catalog = new ApplicationCatalogPayload(UUID.randomUUID(), "", 0, 20,
                1, 1, List.of(new ApplicationListItem("idea", "IntelliJ IDEA")));
        return Stream.concat(Stream.of(AssistantResult.catalog("Catálogo de aplicaciones", catalog)),
                Stream.of(0, 1, 5, 6).map(count -> AssistantResult.openApplications("Aplicaciones abiertas",
                        new OpenApplicationsPayload(java.util.stream.IntStream.range(0, count)
                                .mapToObj(i -> new OpenApplicationItem("app-" + i, "App " + i)).toList(), 0))));
    }

    @Test
    void reservedOutputShouldResumeSpeechAfterReservationEnds() {
        when(exclusiveAudioDetector.isOutputReserved()).thenReturn(true, false);
        audioController.setVolume(40);

        coordinator.present("Solo pantalla");
        assertEquals(AudioDeliveryState.OUTPUT_RESERVED, visualOutput.lastMessage.getAudioDeliveryState());
        assertEquals(0, audioPipeline.speakCalls);

        coordinator.present("Audio disponible");
        assertEquals(List.of("show", "hide", "speak"), events);
        assertEquals("Audio disponible", audioPipeline.spokenText);
    }

    @ParameterizedTest
    @ValueSource(ints = {19, 20, 40})
    void reservedOutputShouldRedirectRequestedVoiceToScreen(int volume) {
        audioController.setVolume(volume);
        when(exclusiveAudioDetector.isOutputReserved()).thenReturn(true);

        coordinator.present("Respuesta reservada");

        assertEquals(0, audioPipeline.speakCalls);
        assertEquals(0, visualOutput.hideCalls);
        assertEquals(1, visualOutput.showCalls);
        assertEquals("Respuesta reservada", visualOutput.lastMessage.getText());
        assertEquals(AudioDeliveryState.OUTPUT_RESERVED, visualOutput.lastMessage.getAudioDeliveryState());
        assertEquals(volume, visualOutput.lastMessage.getAudioSnapshot().getVolume());
    }

    @Test
    void mutedOutputShouldNotConsultReservationOrAttemptSpeech() {
        audioController.mute();
        coordinator.present("Silencio");
        verifyNoInteractions(exclusiveAudioDetector);
        assertEquals(0, audioPipeline.speakCalls);
        assertEquals(1, visualOutput.showCalls);
    }

    @Test
    void reservedOutputShouldRemainSilentEvenWhenShowingTextFails() {
        when(exclusiveAudioDetector.isOutputReserved()).thenReturn(true);
        visualOutput.failOnShow = true;

        assertDoesNotThrow(() -> coordinator.present("Respuesta"));

        assertEquals(0, audioPipeline.speakCalls);
        assertEquals(1, visualOutput.showCalls);
    }

    @ParameterizedTest
    @CsvSource({"0,40,false", "1,40,false", "5,40,false", "6,40,false",
            "0,10,false", "1,10,false", "5,10,false", "6,10,false",
            "0,0,false", "1,0,false", "5,0,false", "6,0,false",
            "0,40,true", "1,40,true", "5,40,true", "6,40,true"})
    void openApplicationsShouldRespectListSizeAndAudioPolicy(int count, int volume, boolean muted) {
        audioController.setVolume(volume);
        if (muted) audioController.mute();
        OpenApplicationsPayload payload = new OpenApplicationsPayload(
                java.util.stream.IntStream.range(0, count)
                        .mapToObj(i -> new OpenApplicationItem("app-" + i, "App " + i)).toList(), 0);

        coordinator.present(AssistantResult.openApplications("Aplicaciones abiertas", payload));

        boolean silent = muted || volume == 0;
        boolean visual = silent || volume < 20 || count > 5;
        assertEquals(silent ? 0 : 1, audioPipeline.speakCalls);
        assertEquals(visual ? 1 : 0, visualOutput.showCalls);
        assertEquals(visual ? 0 : 1, visualOutput.hideCalls);
        if (visual) assertSame(payload, visualOutput.lastMessage.getPayload());
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
        assertThrows(NullPointerException.class, () -> coordinator.present((String) null));

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
    void catalogPayloadShouldForceVisualOutputAndKeepSpeechBrief() {
        audioController.setVolume(40);
        ApplicationCatalogPayload payload = new ApplicationCatalogPayload(UUID.randomUUID(), "", 0, 20,
                1, 1, List.of(new ApplicationListItem("spotify", "Spotify")));

        coordinator.present(AssistantResult.catalog("Encontré una aplicación; te la muestro en pantalla.", payload));

        assertEquals(1, visualOutput.showCalls);
        assertSame(payload, visualOutput.lastMessage.getPayload());
        assertEquals(1, audioPipeline.speakCalls);
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

    @Test
    void shortOpenApplicationListShouldFollowNormalPresentationPolicy() {
        audioController.setVolume(40);
        OpenApplicationsPayload payload = new OpenApplicationsPayload(List.of(
                new OpenApplicationItem("firefox", "Firefox")), 0);

        coordinator.present(AssistantResult.openApplications("Tienes Firefox abierto.", payload));

        assertEquals(1, audioPipeline.speakCalls);
        assertEquals(0, visualOutput.showCalls);
        assertEquals(1, visualOutput.hideCalls);
    }

    @Test
    void longOpenApplicationListShouldForceVisualOutputAtNormalVolume() {
        audioController.setVolume(40);
        OpenApplicationsPayload payload = new OpenApplicationsPayload(
                java.util.stream.IntStream.rangeClosed(1, 6)
                        .mapToObj(index -> new OpenApplicationItem("app-" + index, "App " + index)).toList(), 1);

        coordinator.present(AssistantResult.openApplications("Tienes seis aplicaciones abiertas.", payload));

        assertEquals(1, audioPipeline.speakCalls);
        assertEquals(1, visualOutput.showCalls);
        assertSame(payload, visualOutput.lastMessage.getPayload());
    }

    @Test
    void longOpenApplicationListShouldRemainSilentWhenMuted() {
        audioController.mute();
        OpenApplicationsPayload payload = new OpenApplicationsPayload(
                java.util.stream.IntStream.rangeClosed(1, 6)
                        .mapToObj(index -> new OpenApplicationItem("app-" + index, "App " + index)).toList(), 0);

        coordinator.present(AssistantResult.openApplications("Tienes seis aplicaciones abiertas.", payload));

        assertEquals(0, audioPipeline.speakCalls);
        assertEquals(1, visualOutput.showCalls);
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

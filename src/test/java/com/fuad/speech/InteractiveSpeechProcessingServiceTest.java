package com.fuad.speech;

import com.fuad.activation.ActivationResult;
import com.fuad.assistant.AssistantResult;
import com.fuad.assistant.skills.Skill;
import com.fuad.assistant.skills.SkillExecution;
import com.fuad.assistant.skills.SkillRoute;
import com.fuad.assistant.skills.SkillRouter;
import com.fuad.assistant.session.ConversationSession;
import com.fuad.audio.AssistantAudioController;
import com.fuad.audio.AudioDeviceInfo;
import com.fuad.audio.AudioPlaybackService;
import com.fuad.enums.ActivationType;
import com.fuad.enums.Capability;
import com.fuad.interaction.ChoiceOption;
import com.fuad.interaction.ChoiceRequest;
import com.fuad.interaction.DefaultInteractionService;
import com.fuad.interaction.FocusRequirement;
import com.fuad.interaction.InputModality;
import com.fuad.interaction.InteractionPresenter;
import com.fuad.interaction.InteractionRequest;
import com.fuad.interaction.InteractionResponder;
import com.fuad.interaction.InteractionVoiceRouter;
import com.fuad.pipeline.AssistantPipeline;
import com.fuad.pipeline.AudioPipeline;
import com.fuad.presentation.AssistantOutputCoordinator;
import com.fuad.presentation.OutputPresentationPolicy;
import com.fuad.presentation.VisualMessage;
import com.fuad.presentation.VisualOutput;
import com.fuad.stt.SttEngine;
import com.fuad.stt.TranscriptionResult;
import com.fuad.tts.TtsAudio;
import com.fuad.tts.TtsEngine;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class InteractiveSpeechProcessingServiceTest {
    @Test
    void interactionContinuationShouldRunOnAresExecutorNotCompletingThread() throws Exception {
        TrackingPresenter presenter = new TrackingPresenter();
        DefaultInteractionService interactionService = new DefaultInteractionService(presenter);
        InteractionVoiceRouter voiceRouter = new InteractionVoiceRouter(interactionService);
        AtomicReference<String> continuationThread = new AtomicReference<>();
        Skill skill = new Skill() {
            @Override
            public AssistantResult execute(String command) {
                return new AssistantResult("sync fallback");
            }

            @Override
            public SkillExecution executeTurn(String command) {
                ChoiceRequest request = new ChoiceRequest(Optional.empty(), "Elige",
                        Set.of(InputModality.TOUCH, InputModality.VOICE), Optional.empty(),
                        FocusRequirement.PASSIVE, List.of(
                        new ChoiceOption("one", "Uno", List.of()),
                        new ChoiceOption("two", "Dos", List.of())));
                return new SkillExecution.AwaitingInteraction<>(request, result -> {
                    continuationThread.set(Thread.currentThread().getName());
                    return SkillExecution.completed(new AssistantResult("resuelto"));
                });
            }
        };
        AssistantPipeline assistantPipeline = new AssistantPipeline(router(skill));
        AssistantAudioController audioController = new AssistantAudioController();
        audioController.mute();
        AudioPipeline audio = audioPipeline(audioController);
        TrackingVisualOutput visual = new TrackingVisualOutput();
        AssistantOutputCoordinator output = new AssistantOutputCoordinator(audioController,
                new OutputPresentationPolicy(20), audio, visual);
        SttEngine stt = new SttEngine() {
            @Override
            public TranscriptionResult transcribe(SpeechSegment segment) {
                return new TranscriptionResult("Ares abre studio", "es", 1);
            }

            @Override
            public void close() {
            }
        };

        try (SpeechProcessingService speech = new SpeechProcessingService(stt,
                assistantPipeline,
                ignored -> new ActivationResult(true, ActivationType.WAKE_WORD, "abre studio"),
                new ConversationSession(), audio, ignored ->
                new com.fuad.speech.validation.SpeechValidationResult(true, "ok", 1, 1, 1),
                ignored -> com.fuad.enums.UtteranceDecision.OTHER, output,
                interactionService, voiceRouter)) {
            speech.onSpeechSegment(new SpeechSegment(new float[]{0.2f}, 16_000, 0));
            assertTrue(presenter.presented.await(2, TimeUnit.SECONDS));
            assertTrue(waitUntil(audio::canListen));

            Thread completingThread = new Thread(presenter::selectFirst, "fake-javafx-thread");
            completingThread.start();
            completingThread.join();

            assertTrue(visual.shown.await(2, TimeUnit.SECONDS));
            assertEquals("resuelto", visual.message.get().getText());
            assertNotEquals("fake-javafx-thread", continuationThread.get());
            assertNotNull(continuationThread.get());
        }
        finally {
            interactionService.close();
        }
    }

    private boolean waitUntil(java.util.function.BooleanSupplier condition)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(10);
        }
        return condition.getAsBoolean();
    }

    private SkillRouter router(Skill skill) {
        return new SkillRouter() {
            @Override
            public SkillRoute route(String command) {
                return new SkillRoute(Capability.OS_COMMAND, skill);
            }

            @Override
            public SkillRoute routeTo(Capability capability) {
                return new SkillRoute(capability, skill);
            }
        };
    }

    private AudioPipeline audioPipeline(AssistantAudioController controller) {
        TtsEngine tts = new TtsEngine() {
            @Override
            public TtsAudio synthesize(String text) {
                return new TtsAudio(new float[0], 16_000);
            }

            @Override
            public void close() {
            }
        };
        return new AudioPipeline(tts, new AudioPlaybackService(),
                new AudioDeviceInfo(null, "test", "test", "test"), controller);
    }

    private static final class TrackingPresenter implements InteractionPresenter {
        private final CountDownLatch presented = new CountDownLatch(1);
        private UUID id;
        private InteractionResponder<String> responder;

        @Override
        @SuppressWarnings("unchecked")
        public <T> void present(UUID sessionId, InteractionRequest<T> request,
                                InteractionResponder<T> responder) {
            id = sessionId;
            this.responder = (InteractionResponder<String>) responder;
            responder.visible(sessionId);
            presented.countDown();
        }

        @Override
        public void dismiss(UUID sessionId) {
        }

        void selectFirst() {
            responder.submit(id, "one", InputModality.TOUCH);
        }
    }

    private static final class TrackingVisualOutput implements VisualOutput {
        private final CountDownLatch shown = new CountDownLatch(1);
        private final AtomicReference<VisualMessage> message = new AtomicReference<>();

        @Override
        public void show(VisualMessage visualMessage) {
            message.set(visualMessage);
            shown.countDown();
        }

        @Override
        public void hide() {
        }
    }
}

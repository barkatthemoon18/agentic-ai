package com.fuad.speech;

import com.fuad.activation.ActivationDetector;
import com.fuad.activation.ActivationResult;
import com.fuad.activation.context.ContextContinuationClassifier;
import com.fuad.assistant.*;
import com.fuad.assistant.session.ConversationSession;
import com.fuad.assistant.skills.Skill;
import com.fuad.audio.AudioPlaybackService;
import com.fuad.enums.ActivationType;
import com.fuad.enums.ConversationPolicy;
import com.fuad.pipeline.AssistantPipeline;
import com.fuad.pipeline.AudioPipeline;
import com.fuad.speech.validation.SpeechSegmentValidator;
import com.fuad.speech.validation.SpeechValidationResult;
import com.fuad.stt.SttEngine;
import com.fuad.stt.TranscriptionResult;
import com.fuad.tts.TtsAudio;
import com.fuad.tts.TtsEngine;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class SpeechProcessingServiceTest {
    private final SpeechSegment segment = new SpeechSegment(new float[]{0.2f}, 1_000, 0);

    @Test
    void invalidSegmentShouldNotBeTranscribedAndShouldReleaseAudio() throws Exception {
        AtomicInteger transcriptions = new AtomicInteger();
        TrackingAudioPipeline audio = new TrackingAudioPipeline();
        SttEngine stt = stt(ignored -> { transcriptions.incrementAndGet(); return transcription("hola"); });

        try (SpeechProcessingService service = service(stt, valid(false), ignored -> ActivationResult.none(),
                new ConversationSession(), audio, text -> false, new TrackingAssistantEngine())) {
            service.onSpeechSegment(segment);
            assertTrue(audio.awaitFinished());
        }

        assertEquals(0, transcriptions.get());
    }

    @Test
    void emptyTranscriptionShouldBeIgnoredAndReleaseAudio() throws Exception {
        AtomicBoolean activationCalled = new AtomicBoolean();
        TrackingAudioPipeline audio = new TrackingAudioPipeline();
        ActivationDetector detector = result -> { activationCalled.set(true); return ActivationResult.none(); };

        try (SpeechProcessingService service = service(stt(ignored -> transcription("   ")), valid(true), detector,
                new ConversationSession(), audio, text -> false, new TrackingAssistantEngine())) {
            service.onSpeechSegment(segment);
            assertTrue(audio.awaitFinished());
        }

        assertFalse(activationCalled.get());
        assertNull(audio.spokenText.get());
    }

    @Test
    void activatedCommandShouldExecuteAssistantSpeakAndActivateSession() throws Exception {
        TrackingAudioPipeline audio = new TrackingAudioPipeline();
        ConversationSession session = new ConversationSession();
        AtomicReference<String> executed = new AtomicReference<>();
        Skill skill = new Skill() {
            @Override public AssistantResult execute(String command) { executed.set(command); return new AssistantResult("respuesta"); }
            @Override public ConversationPolicy conversationPolicy() { return ConversationPolicy.KEEP_OPEN; }
        };
        AssistantPipeline assistant = new AssistantPipeline(new TrackingAssistantEngine(), command -> skill);

        try (SpeechProcessingService service = new SpeechProcessingService(
                stt(ignored -> transcription("Ares responde")), assistant,
                ignored -> new ActivationResult(true, ActivationType.WAKE_WORD, "responde"), session,
                audio, valid(true), text -> false)) {
            service.onSpeechSegment(segment);
            assertTrue(audio.awaitFinished());
        }

        assertEquals("responde", executed.get());
        assertEquals("respuesta", audio.spokenText.get());
        assertTrue(session.isActive());
    }

    @Test
    void closePhraseShouldCloseSessionResetAssistantAndSpeakConfirmation() throws Exception {
        TrackingAudioPipeline audio = new TrackingAudioPipeline();
        ConversationSession session = new ConversationSession();
        session.activate();
        TrackingAssistantEngine engine = new TrackingAssistantEngine();
        AtomicBoolean activationCalled = new AtomicBoolean();

        try (SpeechProcessingService service = service(stt(ignored -> transcription("eso es todo")), valid(true),
                ignored -> { activationCalled.set(true); return ActivationResult.none(); },
                session, audio, text -> false, engine)) {
            service.onSpeechSegment(segment);
            assertTrue(audio.awaitFinished());
        }

        assertFalse(session.isActive());
        assertTrue(engine.reset.get());
        assertFalse(activationCalled.get());
        assertEquals("Conversación terminada", audio.spokenText.get());
    }

    @Test
    void activeConversationShouldUseContextualContinuation() throws Exception {
        TrackingAudioPipeline audio = new TrackingAudioPipeline();
        ConversationSession session = new ConversationSession();
        session.activate();
        AtomicBoolean contextCalled = new AtomicBoolean();
        AtomicReference<String> command = new AtomicReference<>();
        AssistantPipeline assistant = new AssistantPipeline(new TrackingAssistantEngine(), routed -> cmd -> {
            command.set(cmd);
            return new AssistantResult("seguimos");
        });

        try (SpeechProcessingService service = new SpeechProcessingService(
                stt(ignored -> transcription("¿y por qué?")), assistant, ignored -> ActivationResult.none(), session,
                audio, valid(true), text -> { contextCalled.set(true); return true; })) {
            service.onSpeechSegment(segment);
            assertTrue(audio.awaitFinished());
        }

        assertTrue(contextCalled.get());
        assertEquals("¿y por qué?", command.get());
        assertEquals("seguimos", audio.spokenText.get());
    }

    @Test
    void expiredConversationShouldResetAssistantBeforeIgnoringNonActivatedText() throws Exception {
        TrackingAudioPipeline audio = new TrackingAudioPipeline();
        ConversationSession session = new ConversationSession();
        expire(session);
        TrackingAssistantEngine engine = new TrackingAssistantEngine();

        try (SpeechProcessingService service = service(stt(ignored -> transcription("comentario")), valid(true),
                ignored -> ActivationResult.none(), session, audio, text -> false, engine)) {
            service.onSpeechSegment(segment);
            assertTrue(audio.awaitFinished());
        }

        assertTrue(engine.reset.get());
        assertFalse(session.isActive());
        assertNull(audio.spokenText.get());
    }

    @Test
    void processingFailureShouldStillReleaseAudioPipeline() throws Exception {
        TrackingAudioPipeline audio = new TrackingAudioPipeline();

        try (SpeechProcessingService service = service(stt(ignored -> { throw new IllegalStateException("stt"); }),
                valid(true), ignored -> ActivationResult.none(), new ConversationSession(), audio,
                text -> false, new TrackingAssistantEngine())) {
            service.onSpeechSegment(segment);
            assertTrue(audio.awaitFinished());
        }

        assertEquals(1, audio.finishCount.get());
    }

    @Test
    void busyAudioPipelineShouldRejectSegmentSynchronously() {
        TrackingAudioPipeline audio = new TrackingAudioPipeline();
        audio.beginResult = false;
        AtomicInteger validations = new AtomicInteger();
        SpeechSegmentValidator validator = ignored -> {
            validations.incrementAndGet();
            return validResult(true);
        };

        try (SpeechProcessingService service = service(stt(ignored -> transcription("hola")), validator,
                ignored -> ActivationResult.none(), new ConversationSession(), audio, text -> false,
                new TrackingAssistantEngine())) {
            service.onSpeechSegment(segment);
        }

        assertEquals(0, validations.get());
        assertEquals(0, audio.finishCount.get());
    }

    private SpeechProcessingService service(SttEngine stt, SpeechSegmentValidator validator,
                                            ActivationDetector activationDetector, ConversationSession session,
                                            TrackingAudioPipeline audio, ContextContinuationClassifier context,
                                            TrackingAssistantEngine engine) {
        AssistantPipeline assistant = new AssistantPipeline(engine, command -> cmd -> new AssistantResult("ok"));
        return new SpeechProcessingService(stt, assistant, activationDetector, session, audio, validator, context);
    }

    private SttEngine stt(java.util.function.Function<SpeechSegment, TranscriptionResult> function) {
        return new SttEngine() {
            @Override public TranscriptionResult transcribe(SpeechSegment value) { return function.apply(value); }
            @Override public void close() { }
        };
    }

    private SpeechSegmentValidator valid(boolean value) {
        return ignored -> validResult(value);
    }

    private SpeechValidationResult validResult(boolean value) {
        return new SpeechValidationResult(value, value ? "valid" : "invalid", 1, 1, 1);
    }

    private TranscriptionResult transcription(String text) {
        return new TranscriptionResult(text, "es", 1);
    }

    private void expire(ConversationSession session) throws ReflectiveOperationException {
        Field field = ConversationSession.class.getDeclaredField("activeUntil");
        field.setAccessible(true);
        field.setLong(session, System.currentTimeMillis() - 1);
    }

    private static final class TrackingAssistantEngine implements AssistantEngine {
        private final AtomicBoolean reset = new AtomicBoolean();
        @Override public AssistantResult process(AssistantRequest request) { return new AssistantResult("ok"); }
        @Override public void resetConversation() { reset.set(true); }
    }

    private static final class TrackingAudioPipeline extends AudioPipeline {
        private final CountDownLatch finished = new CountDownLatch(1);
        private final AtomicInteger finishCount = new AtomicInteger();
        private final AtomicReference<String> spokenText = new AtomicReference<>();
        private boolean beginResult = true;

        private TrackingAudioPipeline() {
            super(new TtsEngine() {
                @Override public TtsAudio synthesize(String text) { return new TtsAudio(new float[0], 16_000); }
                @Override public void close() { }
            }, new AudioPlaybackService(), null);
        }

        @Override public synchronized boolean beginProcessing() { return beginResult; }
        @Override public void speak(String text) { spokenText.set(text); }
        @Override public synchronized void finishProcessing() { finishCount.incrementAndGet(); finished.countDown(); }
        private boolean awaitFinished() throws InterruptedException { return finished.await(2, TimeUnit.SECONDS); }
    }
}

package com.fuad.speech;

import com.fuad.activation.ActivationDetector;
import com.fuad.activation.ActivationResult;
import com.fuad.activation.context.ContextContinuationClassifier;
import com.fuad.activation.context.ContextContinuationRequest;
import com.fuad.assistant.AssistantExecutionResult;
import com.fuad.assistant.AssistantResult;
import com.fuad.assistant.session.ConversationControlDetector;
import com.fuad.assistant.session.ConversationSession;
import com.fuad.assistant.session.ConversationSnapshot;
import com.fuad.enums.ActivationType;
import com.fuad.enums.ContextContinuationDecision;
import com.fuad.enums.ConversationControl;
import com.fuad.enums.ConversationPolicy;
import com.fuad.pipeline.AssistantPipeline;
import com.fuad.pipeline.AudioPipeline;
import com.fuad.speech.validation.SpeechSegmentValidator;
import com.fuad.speech.validation.SpeechValidationResult;
import com.fuad.stt.SttEngine;
import com.fuad.stt.TranscriptionResult;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class SpeechProcessingService implements SpeechSegmentListener, AutoCloseable {
    private final SttEngine sttEngine;
    private final AssistantPipeline assistantPipeline;
    private final ActivationDetector activationDetector;
    private final ConversationSession conversationSession;
    private final AudioPipeline audioPipeline;
    private final SpeechSegmentValidator speechValidator;
    private final ContextContinuationClassifier contextContinuationClassifier;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public SpeechProcessingService(SttEngine sttEngine, AssistantPipeline assistantPipeline,
                                   ActivationDetector activationDetector, ConversationSession session,
                                   AudioPipeline audioPipeline, SpeechSegmentValidator speechValidator,
                                   ContextContinuationClassifier contextContinuationClassifier) {
        this.sttEngine = sttEngine;
        this.assistantPipeline = assistantPipeline;
        this.activationDetector = activationDetector;
        this.conversationSession = session;
        this.audioPipeline = audioPipeline;
        this.speechValidator = speechValidator;
        this.contextContinuationClassifier = contextContinuationClassifier;
    }

    @Override
    public void onSpeechSegment(SpeechSegment segment) {
        if (!audioPipeline.beginProcessing()) {
            System.out.println("Speech segment ignored: audio pipeline busy");
            return;
        }
        executorService.submit(() -> process(segment));
    }

    @Override
    public void close() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    System.err.println("Speech processor did not terminate");
                }
            }
        }
        catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void process(SpeechSegment speechSegment) {
        ActivationResult activationResult;
        ConversationControlDetector controlDetector = new ConversationControlDetector();

        try {
            SpeechValidationResult validationResult = speechValidator.validate(speechSegment);
            System.out.printf("Speech validation | %.0f ms | RMS %.4f | Peak %.4f | %s%n",
                    validationResult.getDurationMillis(), validationResult.getRms(), validationResult.getPeak(),
                    validationResult.getReason());
            if (!validationResult.isValid()) {
                System.out.println("Speech segment ignored");
                return;
            }
            TranscriptionResult result = sttEngine.transcribe(speechSegment);
            String text = result.getText() != null ? result.getText().trim() : "";
            System.out.println("STT: " + text);
            if (text.isEmpty()) {
                System.out.println("STT: empty. Ignored");
                return;
            }
            ConversationControl conversationControl = controlDetector.detect(text);
            if (conversationControl == ConversationControl.CLOSE) {
                System.out.println("CONVERSATION -> FORCE CLOSE");
                conversationSession.close();
                assistantPipeline.resetConversation();
                audioPipeline.speak("Conversación terminada");
                return;
            }
            if (conversationSession.hasExpired()) {
                System.out.println("Conversación expirada");
                conversationSession.close();
                assistantPipeline.resetConversation();
            }
            ActivationResult detected = activationDetector.detect(result);
            if (detected.isActivated()) {
                activationResult = detected;
            }
            else if (conversationSession.isActive()) {
                ConversationSnapshot conversationSnapshot = conversationSession.getSnapshot().orElseThrow(() ->
                        new IllegalStateException("Active conversation has no snapshot"));
                ContextContinuationRequest continuationRequest = new ContextContinuationRequest(conversationSnapshot, text);
                ContextContinuationDecision continuationDecision = contextContinuationClassifier.classify(continuationRequest);
                System.out.println("CONTEXT AI -> " + continuationDecision);
                activationResult = continuationDecision == ContextContinuationDecision.CONTINUE ?
                        new ActivationResult(true, ActivationType.CONTEXTUAL, text) : ActivationResult.none();
            }
            else {
                activationResult = ActivationResult.none();
            }
            if (!activationResult.isActivated()) {
                System.out.println("Activation ignored");
                return;
            }
            AssistantExecutionResult executionResult = assistantPipeline.process(activationResult);
            AssistantResult response = executionResult.getResponse();
            System.out.println("ASSISTANT: " + response.getText());
            audioPipeline.speak(response.getText());
            applyConversationPolicy(executionResult.getConversationPolicy(), activationResult.getCommand(),
                    response.getText());
        }
        catch (Exception e) {
            System.out.println("Error processing speech segment: " + e.getMessage());
            e.printStackTrace();
        }
        finally {
            audioPipeline.finishProcessing();
        }
    }

    private void applyConversationPolicy(ConversationPolicy conversationPolicy, String userText, String assistantText) {
        switch (conversationPolicy) {
            case KEEP_OPEN -> {
                ConversationSnapshot conversationSnapshot = new ConversationSnapshot(userText, assistantText);
                boolean wasActive = conversationSession.isActive();
                conversationSession.openOrRefresh(conversationSnapshot);
                System.out.println("CONVERSATION POLICY: -> " + (wasActive ? "CONVERSATION -> REFRESHED" : "CONVERSATION -> OPENED"));
            }
            case PRESERVE -> System.out.println("PRESERVE. Nothing to do");
        }
    }
}

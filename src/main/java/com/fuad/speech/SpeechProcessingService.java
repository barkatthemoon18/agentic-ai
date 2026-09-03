package com.fuad.speech;

import com.fuad.activation.ActivationDetector;
import com.fuad.activation.ActivationResult;
import com.fuad.activation.utterance.UtteranceClassificationRequest;
import com.fuad.activation.utterance.UtteranceClassifier;
import com.fuad.assistant.AssistantExecutionResult;
import com.fuad.assistant.AssistantResult;
import com.fuad.assistant.session.ConversationControlDetector;
import com.fuad.assistant.session.ConversationSession;
import com.fuad.assistant.session.ConversationSnapshot;
import com.fuad.enums.*;
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
    private final UtteranceClassifier utteranceClassifier;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public SpeechProcessingService(SttEngine sttEngine, AssistantPipeline assistantPipeline,
                                   ActivationDetector activationDetector, ConversationSession session,
                                   AudioPipeline audioPipeline, SpeechSegmentValidator speechValidator,
                                   UtteranceClassifier utteranceClassifier) {
        this.sttEngine = sttEngine;
        this.assistantPipeline = assistantPipeline;
        this.activationDetector = activationDetector;
        this.conversationSession = session;
        this.audioPipeline = audioPipeline;
        this.speechValidator = speechValidator;
        this.utteranceClassifier = utteranceClassifier;
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
            ActivationResult explicitActivation = activationDetector.detect(result);
            if (explicitActivation.isActivated()) {
                activationResult = explicitActivation;
            }
            else {
                UtteranceClassificationRequest request = buildClassificationRequest(text);
                UtteranceDecision decision = utteranceClassifier.classify(request);
                System.out.println("UTTERANCE AI -> " + decision);
                activationResult = mapDecision(decision, text);
            }
            if (!activationResult.isActivated()) {
                System.out.println("Activation ignored");
                return;
            }
            AssistantExecutionResult executionResult;
            if (activationResult.getType() == ActivationType.CONTEXTUAL) {
                Capability owner = conversationSession.getOwner().orElseThrow(() -> new IllegalStateException("Contextual activation without context owner"));
                executionResult = assistantPipeline.processFollowUp(activationResult, owner);
            }
            else {
                executionResult = assistantPipeline.process(activationResult);
            }
            AssistantResult response = executionResult.getResponse();
            System.out.println("ASSISTANT: " + response.getText());
            audioPipeline.speak(response.getText());
            applyConversationPolicy(executionResult, activationResult.getCommand(),
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

    private void applyConversationPolicy(AssistantExecutionResult executionResult, String userText, String assistantText) {
        switch (executionResult.getConversationPolicy()) {
            case KEEP_OPEN -> {
                ConversationSnapshot conversationSnapshot = new ConversationSnapshot(executionResult.getCapability(),
                        userText, assistantText);
                boolean wasActive = conversationSession.isActive();
                conversationSession.openOrRefresh(conversationSnapshot);
                System.out.println("CONVERSATION POLICY: -> " + (wasActive ? "CONVERSATION -> REFRESHED" : "CONVERSATION -> OPENED"));
            }
            case PRESERVE -> System.out.println("PRESERVE. Nothing to do");
        }
    }

    private UtteranceClassificationRequest buildClassificationRequest(String text) {
        return conversationSession.getSnapshot().map(snapshot ->
                UtteranceClassificationRequest.withContext(text, snapshot)).orElseGet(() ->
                UtteranceClassificationRequest.withoutContext(text));
    }

    private ActivationResult mapDecision(UtteranceDecision utteranceDecision, String text) {
        return switch(utteranceDecision) {
            case NEW_REQUEST -> new ActivationResult(true, ActivationType.SEMANTIC_INTENT, text);
            case FOLLOW_UP -> {
                if (conversationSession.getOwner().isEmpty()) {
                    System.out.println("FOLLOW_UP rejected: no active context owner");
                    yield ActivationResult.none();
                }
                yield new ActivationResult(true, ActivationType.CONTEXTUAL, text);
            }
            case OTHER -> ActivationResult.none();
        };
    }
}

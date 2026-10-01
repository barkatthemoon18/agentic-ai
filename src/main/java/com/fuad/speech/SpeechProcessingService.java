package com.fuad.speech;

import com.fuad.activation.ActivationDetector;
import com.fuad.activation.ActivationResult;
import com.fuad.activation.utterance.UtteranceClassificationRequest;
import com.fuad.activation.utterance.UtteranceClassifier;
import com.fuad.assistant.AssistantExecutionResult;
import com.fuad.assistant.AssistantResult;
import com.fuad.assistant.AssistantTurn;
import com.fuad.assistant.session.ConversationControlDetector;
import com.fuad.assistant.session.ConversationSession;
import com.fuad.assistant.session.ConversationSnapshot;
import com.fuad.enums.*;
import com.fuad.pipeline.AssistantPipeline;
import com.fuad.pipeline.AudioPipeline;
import com.fuad.presentation.AssistantOutputCoordinator;
import com.fuad.interaction.InteractionService;
import com.fuad.interaction.InteractionVoiceRouter;
import com.fuad.interaction.VoiceRouteOutcome;
import com.fuad.speech.validation.SpeechSegmentValidator;
import com.fuad.speech.validation.SpeechValidationResult;
import com.fuad.stt.SttEngine;
import com.fuad.stt.TranscriptionResult;
import lombok.NonNull;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public class SpeechProcessingService implements SpeechSegmentListener, AutoCloseable {
    @NonNull
    private final SttEngine sttEngine;
    @NonNull
    private final AssistantPipeline assistantPipeline;
    @NonNull
    private final ActivationDetector activationDetector;
    @NonNull
    private final ConversationSession conversationSession;
    @NonNull
    private final AudioPipeline audioPipeline;
    @NonNull
    private final SpeechSegmentValidator speechValidator;
    @NonNull
    private final UtteranceClassifier utteranceClassifier;
    @NonNull
    private final AssistantOutputCoordinator assistantOutputCoordinator;
    private final InteractionService interactionService;
    private final InteractionVoiceRouter interactionVoiceRouter;
    @NonNull
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final AtomicBoolean pendingTurn = new AtomicBoolean(false);

    public SpeechProcessingService(SttEngine sttEngine,
                                   AssistantPipeline assistantPipeline,
                                   ActivationDetector activationDetector,
                                   ConversationSession conversationSession,
                                   AudioPipeline audioPipeline,
                                   SpeechSegmentValidator speechValidator,
                                   UtteranceClassifier utteranceClassifier,
                                   AssistantOutputCoordinator assistantOutputCoordinator) {
        this(sttEngine, assistantPipeline, activationDetector, conversationSession,
                audioPipeline, speechValidator, utteranceClassifier,
                assistantOutputCoordinator, null, null);
    }

    public SpeechProcessingService(SttEngine sttEngine,
                                   AssistantPipeline assistantPipeline,
                                   ActivationDetector activationDetector,
                                   ConversationSession conversationSession,
                                   AudioPipeline audioPipeline,
                                   SpeechSegmentValidator speechValidator,
                                   UtteranceClassifier utteranceClassifier,
                                   AssistantOutputCoordinator assistantOutputCoordinator,
                                   InteractionService interactionService,
                                   InteractionVoiceRouter interactionVoiceRouter) {
        this.sttEngine = Objects.requireNonNull(sttEngine);
        this.assistantPipeline = Objects.requireNonNull(assistantPipeline);
        this.activationDetector = Objects.requireNonNull(activationDetector);
        this.conversationSession = Objects.requireNonNull(conversationSession);
        this.audioPipeline = Objects.requireNonNull(audioPipeline);
        this.speechValidator = Objects.requireNonNull(speechValidator);
        this.utteranceClassifier = Objects.requireNonNull(utteranceClassifier);
        this.assistantOutputCoordinator = Objects.requireNonNull(assistantOutputCoordinator);
        this.interactionService = interactionService;
        this.interactionVoiceRouter = interactionVoiceRouter;
        if ((interactionService == null) != (interactionVoiceRouter == null)) {
            throw new IllegalArgumentException(
                    "interactionService and interactionVoiceRouter must be provided together");
        }
    }

    @Override
    public void onSpeechSegment(SpeechSegment segment) {
        if (pendingTurn.get()
                && (interactionVoiceRouter == null
                || !interactionVoiceRouter.hasActiveInteraction())) {
            System.out.println("Speech segment ignored: assistant turn pending");
            return;
        }
        if (!audioPipeline.beginProcessing()) {
            System.out.println("Speech segment ignored: audio pipeline busy");
            return;
        }
        try {
            executorService.submit(() -> process(segment));
        }
        catch (RejectedExecutionException e) {
            audioPipeline.finishProcessing();
            System.err.println("Speech segment ignored: executor rejected task: " + e.getMessage());
        }
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

    public boolean submitDirectTurn(String userText, Supplier<AssistantTurn> turnSupplier) {
        Objects.requireNonNull(userText, "userText must not be null");
        Objects.requireNonNull(turnSupplier, "turnSupplier must not be null");

        if (pendingTurn.get()) {
            return false;
        }
        if (!audioPipeline.beginProcessing()) {
            return false;
        }
        try {
            executorService.execute(() -> processDirectTurn(userText, turnSupplier));
            return true;
        }
        catch (RejectedExecutionException e) {
            audioPipeline.finishProcessing();
            System.err.println("Direct assistant turn rejected: " + e.getMessage());
            return false;
        }
    }

    private void processDirectTurn(String userText, Supplier<AssistantTurn> turnSupplier) {
        try {
            AssistantTurn turn = turnSupplier.get();
            advanceTurn(turn, userText);
        }
        catch (Exception e) {
            presentProcessingFailure(e);
        }
        finally {
            audioPipeline.finishProcessing();
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
            if (interactionVoiceRouter != null) {
                VoiceRouteOutcome voiceOutcome = interactionVoiceRouter.route(text);
                if (voiceOutcome != VoiceRouteOutcome.NO_ACTIVE_INTERACTION) {
                    System.out.println("INTERACTION VOICE -> " + voiceOutcome);
                    return;
                }
            }
            ConversationControl conversationControl = controlDetector.detect(text);
            if (conversationControl == ConversationControl.CLOSE) {
                System.out.println("CONVERSATION -> FORCE CLOSE");
                conversationSession.close();
                assistantOutputCoordinator.present("Conversación terminada");
                return;
            }
            if (conversationSession.hasExpired()) {
                System.out.println("Conversación expirada");
                conversationSession.close();
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
            AssistantTurn turn;
            if (activationResult.getType() == ActivationType.CONTEXTUAL) {
                ConversationSnapshot conversationSnapshot = conversationSession.getSnapshot().orElseThrow(() ->
                        new IllegalStateException("Contextual activation without conversation snapshot"));
                turn = assistantPipeline.processFollowUpTurn(activationResult, conversationSnapshot);
            }
            else {
                turn = assistantPipeline.processTurn(activationResult);
            }
            advanceTurn(turn, activationResult.getCommand());
        }
        catch (Exception e) {
            System.err.println("Error processing speech segment: " + e.getMessage());
            e.printStackTrace();
            try {
                assistantOutputCoordinator.present("No pude completar la solicitud en este momento.");
            }
            catch (Exception presentationFailure) {
                System.err.println("Unable to present processing failure: " + presentationFailure.getMessage());
            }
        }
        finally {
            audioPipeline.finishProcessing();
        }
    }

    private void advanceTurn(AssistantTurn turn, String userText) {
        switch (turn) {
            case AssistantTurn.Completed completed ->
                    finishExecution(completed.result(), userText);
            case AssistantTurn.Async async -> suspendAsync(async, userText);
            case AssistantTurn.AwaitingInteraction<?> awaiting ->
                    suspendForInteraction(awaiting, userText);
        }
    }

    private void finishExecution(AssistantExecutionResult executionResult, String userText) {
        AssistantResult response = executionResult.getResponse();
        System.out.println("ASSISTANT: " + response.getText());
        assistantOutputCoordinator.present(response);
        applyConversationPolicy(executionResult, userText, response.getText());
    }

    private void suspendAsync(AssistantTurn.Async async, String userText) {
        if (!pendingTurn.compareAndSet(false, true)) {
            throw new IllegalStateException("Another assistant turn is already pending");
        }
        try {
            async.stage().whenComplete((result, failure) ->
                    enqueueContinuation(() -> resumeAsync(result, failure, userText)));
        }
        catch (RuntimeException e) {
            pendingTurn.set(false);
            throw e;
        }
    }

    private void resumeAsync(AssistantExecutionResult result, Throwable failure,
                             String userText) {
        pendingTurn.set(false);
        resumeProcessing(() -> {
            if (failure != null) {
                presentProcessingFailure(failure);
            }
            else {
                finishExecution(result, userText);
            }
        });
    }

    private <T> void suspendForInteraction(AssistantTurn.AwaitingInteraction<T> awaiting,
                                           String userText) {
        if (interactionService == null) {
            throw new IllegalStateException("Interactive turn requested without InteractionService");
        }
        if (!pendingTurn.compareAndSet(false, true)) {
            throw new IllegalStateException("Another assistant turn is already pending");
        }
        try {
            interactionService.request(awaiting.request()).whenComplete((result, failure) ->
                    enqueueContinuation(() -> resumeInteraction(awaiting, result, failure, userText)));
        }
        catch (RuntimeException e) {
            pendingTurn.set(false);
            throw e;
        }
    }

    private <T> void resumeInteraction(AssistantTurn.AwaitingInteraction<T> awaiting,
                                       com.fuad.interaction.InteractionResult<T> result,
                                       Throwable failure, String userText) {
        pendingTurn.set(false);
        resumeProcessing(() -> {
            if (failure != null) {
                presentProcessingFailure(failure);
                return;
            }
            // The domain continuation is invoked only here, on Ares' logical executor.
            advanceTurn(awaiting.continuation().apply(result), userText);
        });
    }

    private void resumeProcessing(Runnable action) {
        boolean acquired = audioPipeline.beginProcessing();
        try {
            action.run();
        }
        catch (Exception e) {
            presentProcessingFailure(e);
        }
        finally {
            if (acquired) {
                audioPipeline.finishProcessing();
            }
        }
    }

    private void enqueueContinuation(Runnable continuation) {
        try {
            executorService.execute(continuation);
        }
        catch (RejectedExecutionException e) {
            pendingTurn.set(false);
            System.err.println("Assistant continuation rejected: " + e.getMessage());
        }
    }

    private void presentProcessingFailure(Throwable failure) {
        System.err.println("Error completing assistant turn: " + failure.getMessage());
        try {
            assistantOutputCoordinator.present("No pude completar la solicitud en este momento.");
        }
        catch (Exception presentationFailure) {
            System.err.println("Unable to present processing failure: "
                    + presentationFailure.getMessage());
        }
    }

    private void applyConversationPolicy(AssistantExecutionResult executionResult, String userText, String assistantText) {
        switch (executionResult.getConversationPolicy()) {
            case KEEP_OPEN -> {
                ConversationSnapshot conversationSnapshot = new ConversationSnapshot(executionResult.getCapability(),
                        userText, assistantText, executionResult.getResponse().getContinuationToken(),
                        executionResult.getResponse().getResearchConversationState(),
                        executionResult.getResponse().getGeneralConversationState(),
                        executionResult.getResponse().getOsConversationState());
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

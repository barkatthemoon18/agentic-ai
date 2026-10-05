package com.fuad.speech;

import com.fuad.assistant.*;
import com.fuad.assistant.session.ConversationSession;
import com.fuad.enums.Capability;
import com.fuad.pipeline.ConversationPolicy;
import com.fuad.interaction.*;
import com.fuad.pipeline.*;
import com.fuad.audio.*;
import com.fuad.presentation.AssistantOutputCoordinator;
import com.fuad.speech.validation.SpeechValidationResult;
import com.fuad.stt.SttEngine;
import com.fuad.tts.TtsEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AsyncSpeechProcessingServiceTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void asyncCompletionShouldResumeOnAssistantExecutorAndReleasePendingTurn(boolean failed) throws Exception {
        try (Fixture fixture = new Fixture(false)) {
            CompletableFuture<AssistantExecutionResult> future = new CompletableFuture<>();
            AtomicReference<String> startThread = new AtomicReference<>();
            assertTrue(fixture.service.submitDirectTurn("direct command", () -> {
                startThread.set(Thread.currentThread().getName());
                return new AssistantTurn.Async(future);
            }));
            fixture.awaitFinish();
            assertTrue(fixture.audio.canListen());
            assertTrue(fixture.outputs.isEmpty());
            assertFalse(fixture.session.isActive());

            AtomicInteger rejectedCalls = new AtomicInteger();
            assertFalse(fixture.service.submitDirectTurn("another", () -> {
                rejectedCalls.incrementAndGet();
                return completed("unexpected");
            }));
            fixture.service.onSpeechSegment(new SpeechSegment(new float[]{0.1f}, 16_000, 0));
            assertEquals(0, rejectedCalls.get());
            verifyNoInteractions(fixture.stt);

            Thread completing = new Thread(() -> {
                if (failed) future.completeExceptionally(new IllegalStateException("backend offline"));
                else future.complete(result("finished"));
            }, "fake-backend-thread");
            completing.start();
            completing.join(2000);
            assertFalse(completing.isAlive());
            Output output = fixture.awaitOutput();
            fixture.awaitFinish();
            assertEquals(failed ? "No pude completar la solicitud en este momento." : "finished", output.text());
            assertEquals(startThread.get(), output.thread());
            assertNotEquals("fake-backend-thread", output.thread());
            assertTrue(fixture.audio.canListen());
            assertEquals(!failed, fixture.session.isActive());
            if (!failed) {
                var snapshot = fixture.session.getSnapshot().orElseThrow();
                assertEquals("direct command", snapshot.previousUserText());
                assertEquals("finished", snapshot.previousAssistantText());
            }

            assertTrue(fixture.service.submitDirectTurn("after completion", () -> completed("next")));
            assertEquals("next", fixture.awaitOutput().text());
            fixture.awaitFinish();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void interactionCompletionOrFailureShouldResumeOnAssistantExecutor(boolean failed) throws Exception {
        try (Fixture fixture = new Fixture(true)) {
            ChoiceRequest request = choice();
            CompletableFuture<InteractionResult<String>> selected = new CompletableFuture<>();
            when(fixture.interaction.request(request)).thenReturn(selected);
            AtomicReference<String> continuationThread = new AtomicReference<>();
            assertTrue(fixture.service.submitDirectTurn("choose application", () -> new AssistantTurn.AwaitingInteraction<>(
                    request, result -> {
                        continuationThread.set(Thread.currentThread().getName());
                        return completed(result.outcome() == InteractionOutcome.SUBMITTED ? "selected" : "cancelled");
                    })));
            fixture.awaitFinish();
            assertFalse(fixture.service.submitDirectTurn("pending", () -> completed("unexpected")));

            if (failed) selected.completeExceptionally(new IllegalStateException("presenter failed"));
            else selected.complete(InteractionResult.terminal(request, InteractionOutcome.CANCELLED, InputModality.TOUCH));
            Output output = fixture.awaitOutput();
            fixture.awaitFinish();
            assertEquals(failed ? "No pude completar la solicitud en este momento." : "cancelled", output.text());
            if (failed) assertNull(continuationThread.get());
            else assertEquals(continuationThread.get(), output.thread());
            assertTrue(fixture.audio.canListen());
            assertTrue(fixture.service.submitDirectTurn("after interaction", () -> completed("next")));
            assertEquals("next", fixture.awaitOutput().text());
            fixture.awaitFinish();
        }
    }

    @Test
    void directSupplierFailureShouldPresentErrorReleaseAudioAndPermitNextTurn() throws Exception {
        try (Fixture fixture = new Fixture(false)) {
            assertTrue(fixture.service.submitDirectTurn("direct", () -> { throw new IllegalStateException("action"); }));
            assertEquals("No pude completar la solicitud en este momento.", fixture.awaitOutput().text());
            fixture.awaitFinish();
            assertTrue(fixture.audio.canListen());
            assertFalse(fixture.session.isActive());
            assertTrue(fixture.service.submitDirectTurn("next", () -> completed("ok")));
            assertEquals("ok", fixture.awaitOutput().text());
            fixture.awaitFinish();
        }
    }

    @Test
    void completedAsyncTurnAfterCloseShouldNotProduceOutputAndDirectSubmissionShouldReleaseAudio() throws Exception {
        try (Fixture fixture = new Fixture(false)) {
            CompletableFuture<AssistantExecutionResult> future = new CompletableFuture<>();
            assertTrue(fixture.service.submitDirectTurn("direct", () -> new AssistantTurn.Async(future)));
            fixture.awaitFinish();
            fixture.service.close();
            future.complete(result("too late"));
            verifyNoInteractions(fixture.output);
            assertFalse(fixture.service.submitDirectTurn("closed", () -> { fail("Supplier must not execute"); return completed("unexpected"); }));
            assertTrue(fixture.audio.canListen());
        }
    }

    @Test
    void busyAudioShouldRejectDirectTurnBeforeCallingSupplier() throws Exception {
        try (Fixture fixture = new Fixture(false)) {
            assertTrue(fixture.audio.beginProcessing());
            assertFalse(fixture.service.submitDirectTurn("busy", () -> { fail("Supplier must not execute"); return completed("unexpected"); }));
            assertTrue(fixture.audio.isProcessing());
            verifyNoInteractions(fixture.output);
            fixture.audio.finishProcessing();
        }
    }

    private static AssistantExecutionResult result(String text) {
        return new AssistantExecutionResult(new AssistantResult(text), ConversationPolicy.KEEP_OPEN, Capability.OS_COMMAND);
    }

    private static AssistantTurn.Completed completed(String text) { return new AssistantTurn.Completed(result(text)); }

    private static ChoiceRequest choice() {
        return new ChoiceRequest(Optional.empty(), "Choose", Set.of(InputModality.TOUCH), Optional.empty(),
                FocusRequirement.PASSIVE, List.of(new ChoiceOption("one", "One", List.of()), new ChoiceOption("two", "Two", List.of())));
    }

    private record Output(String text, String thread) { }

    private static final class Fixture implements AutoCloseable {
        private final LinkedBlockingQueue<Boolean> finishes = new LinkedBlockingQueue<>();
        private final LinkedBlockingQueue<Output> outputs = new LinkedBlockingQueue<>();
        private final ConversationSession session = new ConversationSession();
        private final SttEngine stt = mock(SttEngine.class);
        private final AssistantOutputCoordinator output = mock(AssistantOutputCoordinator.class);
        private final InteractionService interaction = mock(InteractionService.class);
        private final AudioPipeline audio = new AudioPipeline(mock(TtsEngine.class), mock(AudioPlaybackService.class), null,
                new AssistantAudioController()) {
            @Override public synchronized void finishProcessing() {
                super.finishProcessing();
                finishes.add(true);
            }
        };
        private final SpeechProcessingService service;

        Fixture(boolean withInteraction) {
            doAnswer(invocation -> {
                outputs.add(new Output(((AssistantResult) invocation.getArgument(0)).text(), Thread.currentThread().getName()));
                return null;
            }).when(output).present(any(AssistantResult.class));
            doAnswer(invocation -> {
                outputs.add(new Output(invocation.getArgument(0), Thread.currentThread().getName()));
                return null;
            }).when(output).present(anyString());
            service = new SpeechProcessingService(stt, mock(AssistantPipeline.class),
                    ignored -> com.fuad.activation.ActivationResult.none(), session, audio,
                    ignored -> new SpeechValidationResult(true, "ok", 1, 1, 1),
                    ignored -> com.fuad.enums.UtteranceDecision.OTHER, output,
                    withInteraction ? interaction : null,
                    withInteraction ? mock(InteractionVoiceRouter.class) : null);
        }

        void awaitFinish() throws InterruptedException { assertNotNull(finishes.poll(3, TimeUnit.SECONDS)); }
        Output awaitOutput() throws InterruptedException {
            Output result = outputs.poll(3, TimeUnit.SECONDS);
            assertNotNull(result);
            return result;
        }
        @Override public void close() { service.close(); }
    }
}

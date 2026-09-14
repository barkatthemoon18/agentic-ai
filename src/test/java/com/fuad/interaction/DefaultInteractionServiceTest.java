package com.fuad.interaction;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class DefaultInteractionServiceTest {
    private final TrackingPresenter presenter = new TrackingPresenter();
    private final TrackingScheduler scheduler = new TrackingScheduler();
    private final DefaultInteractionService service = new DefaultInteractionService(
            presenter, scheduler, Duration.ofMillis(80));

    @AfterEach
    void close() {
        service.close();
    }

    @Test
    void shouldNotExposeOrStartTimeoutBeforePresenterReportsVisible() throws Exception {
        CompletionStage<InteractionResult<String>> stage = service.request(choice());
        CompletableFuture<InteractionResult<String>> callerFuture = stage.toCompletableFuture();
        callerFuture.complete(InteractionResult.terminal(choice(),
                InteractionOutcome.CANCELLED, InputModality.TOUCH));

        Thread.sleep(120);
        assertFalse(stage.toCompletableFuture().isDone());
        assertEquals(0, scheduler.scheduleCount);

        presenter.visible();
        InteractionResult<String> result = stage.toCompletableFuture().get(1, TimeUnit.SECONDS);

        assertEquals(InteractionOutcome.EXPIRED, result.outcome());
        assertEquals(1, scheduler.scheduleCount);
        assertEquals(presenter.sessionId, presenter.dismissedSessionId);
    }

    @Test
    void shouldRejectSecondRequestWithoutCreatingAnotherSession() throws Exception {
        CompletionStage<InteractionResult<String>> first = service.request(choice());
        CompletionStage<InteractionResult<Boolean>> second = service.request(confirmation());

        assertEquals(InteractionOutcome.BUSY,
                second.toCompletableFuture().get().outcome());
        assertEquals(1, presenter.presentCount);
        assertFalse(first.toCompletableFuture().isDone());
    }

    @Test
    void terminalShouldCancelScheduledTimeoutAndIgnoreLateResponses() throws Exception {
        CompletionStage<InteractionResult<String>> stage = service.request(choice());
        presenter.visible();
        presenter.submit("one");

        InteractionResult<String> result = stage.toCompletableFuture().get();

        assertEquals(InteractionOutcome.SUBMITTED, result.outcome());
        assertEquals("one", result.value().orElseThrow());
        assertTrue(scheduler.lastFuture.isCancelled());
        presenter.cancel();
        assertEquals(InteractionOutcome.SUBMITTED, stage.toCompletableFuture().get().outcome());
    }

    @Test
    void unavailableAndCancellationShouldBeTerminalWhilePresenting() throws Exception {
        CompletionStage<InteractionResult<String>> unavailable = service.request(choice());
        presenter.unavailable();
        assertEquals(InteractionOutcome.UNAVAILABLE,
                unavailable.toCompletableFuture().get().outcome());
        assertEquals(0, scheduler.scheduleCount);

        CompletionStage<InteractionResult<String>> cancelled = service.request(choice());
        presenter.cancel();
        assertEquals(InteractionOutcome.CANCELLED,
                cancelled.toCompletableFuture().get().outcome());
    }

    private ChoiceRequest choice() {
        return new ChoiceRequest(Optional.of("external-request"), "Elige",
                Set.of(InputModality.TOUCH, InputModality.VOICE), Optional.empty(),
                FocusRequirement.PASSIVE, List.of(
                new ChoiceOption("one", "Uno", List.of()),
                new ChoiceOption("two", "Dos", List.of())));
    }

    private ConfirmationRequest confirmation() {
        return new ConfirmationRequest(Optional.empty(), "Confirma",
                Set.of(InputModality.TOUCH), Optional.empty(),
                FocusRequirement.PASSIVE, "Sí", "No");
    }

    private static final class TrackingPresenter implements InteractionPresenter {
        private UUID sessionId;
        private InteractionResponder<Object> responder;
        private int presentCount;
        private UUID dismissedSessionId;

        @Override
        @SuppressWarnings("unchecked")
        public <T> void present(UUID sessionId, InteractionRequest<T> request,
                                InteractionResponder<T> responder) {
            this.sessionId = sessionId;
            this.responder = (InteractionResponder<Object>) responder;
            presentCount++;
        }

        @Override
        public void dismiss(UUID sessionId) {
            dismissedSessionId = sessionId;
        }

        void visible() {
            responder.visible(sessionId);
        }

        void submit(Object value) {
            responder.submit(sessionId, value, InputModality.TOUCH);
        }

        void cancel() {
            responder.cancel(sessionId, InputModality.TOUCH);
        }

        void unavailable() {
            responder.unavailable(sessionId, "missing");
        }
    }

    private static final class TrackingScheduler extends ScheduledThreadPoolExecutor {
        private int scheduleCount;
        private ScheduledFuture<?> lastFuture;

        private TrackingScheduler() {
            super(1);
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            scheduleCount++;
            lastFuture = super.schedule(command, delay, unit);
            return lastFuture;
        }
    }
}

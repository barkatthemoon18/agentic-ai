package com.fuad.interaction;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class DefaultInteractionService implements InteractionService {
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(90);

    private final InteractionPresenter presenter;
    private final ScheduledExecutorService scheduler;
    private final Duration defaultTimeout;
    private final AtomicReference<Session<?>> active = new AtomicReference<>();
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public DefaultInteractionService(InteractionPresenter presenter) {
        this(presenter, Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "ares-interaction-timeout");
            thread.setDaemon(true);
            return thread;
        }), DEFAULT_TIMEOUT);
    }

    DefaultInteractionService(InteractionPresenter presenter,
                              ScheduledExecutorService scheduler,
                              Duration defaultTimeout) {
        this.presenter = Objects.requireNonNull(presenter, "presenter must not be null");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler must not be null");
        this.defaultTimeout = Objects.requireNonNull(defaultTimeout, "defaultTimeout must not be null");
        if (defaultTimeout.isZero() || defaultTimeout.isNegative()) {
            throw new IllegalArgumentException("defaultTimeout must be positive");
        }
    }

    @Override
    public <T> CompletionStage<InteractionResult<T>> request(InteractionRequest<T> request) {
        Objects.requireNonNull(request, "request must not be null");
        if (closed.get()) {
            return CompletableFuture.completedStage(InteractionResult.terminal(
                    request, InteractionOutcome.CLOSED, null));
        }
        Session<T> session = new Session<>(UUID.randomUUID(), request);
        if (!active.compareAndSet(null, session)) {
            return CompletableFuture.completedStage(InteractionResult.terminal(
                    request, InteractionOutcome.BUSY, null));
        }
        if (closed.get()) {
            complete(session, InteractionOutcome.CLOSED, null, null);
            return session.result.minimalCompletionStage();
        }
        try {
            presenter.present(session.id, request, new BoundResponder<>(session));
        }
        catch (RuntimeException e) {
            complete(session, InteractionOutcome.UNAVAILABLE, null, null);
        }
        return session.result.minimalCompletionStage();
    }

    Optional<ActiveInteractionSnapshot> activeSnapshot() {
        Session<?> session = active.get();
        if (session == null) {
            return Optional.empty();
        }
        synchronized (session) {
            if (session.phase == InteractionPhase.COMPLETED || active.get() != session) {
                return Optional.empty();
            }
            return Optional.of(new ActiveInteractionSnapshot(session.id, session.request, session.phase));
        }
    }

    boolean submitVoice(UUID sessionId, Object value) {
        Session<?> session = active.get();
        return session != null && session.id.equals(sessionId)
                && completeUntyped(session, InteractionOutcome.SUBMITTED, value, InputModality.VOICE);
    }

    boolean cancelVoice(UUID sessionId) {
        Session<?> session = active.get();
        return session != null && session.id.equals(sessionId)
                && completeUntyped(session, InteractionOutcome.CANCELLED, null, InputModality.VOICE);
    }

    private void markVisible(Session<?> session) {
        synchronized (session) {
            if (active.get() != session || session.phase != InteractionPhase.PRESENTING) {
                return;
            }
            session.phase = InteractionPhase.VISIBLE;
            Duration timeout = session.request.timeoutOverride().orElse(defaultTimeout);
            try {
                session.timeout = scheduler.schedule(() -> {
                    completeUntyped(session, InteractionOutcome.EXPIRED, null, null);
                }, timeout.toMillis(), TimeUnit.MILLISECONDS);
            }
            catch (RuntimeException e) {
                session.phase = InteractionPhase.PRESENTING;
                completeUntyped(session, InteractionOutcome.UNAVAILABLE, null, null);
            }
        }
    }

    private boolean completeUntyped(Session<?> session, InteractionOutcome outcome,
                                    Object value, InputModality modality) {
        @SuppressWarnings("unchecked")
        Session<Object> typed = (Session<Object>) session;
        return complete(typed, outcome, value, modality);
    }

    private <T> boolean complete(Session<T> session, InteractionOutcome outcome,
                                 T value, InputModality modality) {
        ScheduledFuture<?> timeout;
        synchronized (session) {
            if (active.get() != session || session.phase == InteractionPhase.COMPLETED) {
                return false;
            }
            if ((outcome == InteractionOutcome.SUBMITTED || outcome == InteractionOutcome.EXPIRED)
                    && session.phase != InteractionPhase.VISIBLE) {
                return false;
            }
            session.phase = InteractionPhase.COMPLETED;
            timeout = session.timeout;
            session.timeout = null;
        }
        if (timeout != null) {
            timeout.cancel(false);
        }
        active.compareAndSet(session, null);
        InteractionResult<T> interactionResult = outcome == InteractionOutcome.SUBMITTED
                ? InteractionResult.submitted(session.request, value, modality)
                : InteractionResult.terminal(session.request, outcome, modality);
        session.result.complete(interactionResult);
        presenter.dismiss(session.id);
        return true;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        Session<?> session = active.get();
        if (session != null) {
            completeUntyped(session, InteractionOutcome.CLOSED, null, null);
        }
        scheduler.shutdownNow();
        presenter.close();
    }

    private final class BoundResponder<T> implements InteractionResponder<T> {
        private final Session<T> session;

        private BoundResponder(Session<T> session) {
            this.session = session;
        }

        @Override
        public void visible(UUID sessionId) {
            if (session.id.equals(sessionId)) {
                markVisible(session);
            }
        }

        @Override
        public void submit(UUID sessionId, T value, InputModality modality) {
            if (session.id.equals(sessionId)) {
                complete(session, InteractionOutcome.SUBMITTED,
                        Objects.requireNonNull(value), Objects.requireNonNull(modality));
            }
        }

        @Override
        public void cancel(UUID sessionId, InputModality modality) {
            if (session.id.equals(sessionId)) {
                complete(session, InteractionOutcome.CANCELLED, null,
                        Objects.requireNonNull(modality));
            }
        }

        @Override
        public void unavailable(UUID sessionId, String reason) {
            if (session.id.equals(sessionId)) {
                complete(session, InteractionOutcome.UNAVAILABLE, null, null);
            }
        }
    }

    private static final class Session<T> {
        private final UUID id;
        private final InteractionRequest<T> request;
        private final CompletableFuture<InteractionResult<T>> result = new CompletableFuture<>();
        private InteractionPhase phase = InteractionPhase.PRESENTING;
        private ScheduledFuture<?> timeout;

        private Session(UUID id, InteractionRequest<T> request) {
            this.id = id;
            this.request = request;
        }
    }
}

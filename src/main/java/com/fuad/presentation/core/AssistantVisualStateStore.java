package com.fuad.presentation.core;

import com.fuad.pipeline.AssistantActivityListener;
import com.fuad.pipeline.AssistantActivityState;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public final class AssistantVisualStateStore implements AssistantActivityListener {
    private final Object lock = new Object();
    private final CopyOnWriteArrayList<Consumer<AssistantVisualState>> listeners = new CopyOnWriteArrayList<>();
    private AssistantActivityState activityState = AssistantActivityState.IDLE;
    private AssistantVisualState effectiveState = AssistantVisualState.IDLE;
    private boolean degraded;

    @Override
    public void onStateChanged(AssistantActivityState state) {
        AssistantVisualState changed = null;

        Objects.requireNonNull(state);
        synchronized (lock) {
            activityState = state;
            changed = recomputeLocked();
        }
        publish(changed);
    }

    public void setDegraded(boolean degraded) {
        AssistantVisualState changed;

        synchronized (lock) {
            this.degraded = degraded;
            changed = recomputeLocked();
        }
        publish(changed);
    }

    public AssistantVisualState current() {
        synchronized (lock) {
            return effectiveState;
        }
    }

    public AutoCloseable subscribe(Consumer<AssistantVisualState> listener) {
        Objects.requireNonNull(listener);

        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    private AssistantVisualState recomputeLocked() {
        AssistantVisualState next = resolveVisualState();

        if (next == effectiveState) {
            return null;
        }
        effectiveState = next;
        return next;
    }

    private AssistantVisualState resolveVisualState() {
        if (activityState == AssistantActivityState.IDLE && degraded) {
            return AssistantVisualState.DEGRADED;
        }
        return switch (activityState) {
            case IDLE -> AssistantVisualState.IDLE;
            case LISTENING -> AssistantVisualState.LISTENING;
            case PROCESSING -> AssistantVisualState.PROCESSING;
            case SPEAKING -> AssistantVisualState.SPEAKING;
        };
    }

    private void publish(AssistantVisualState state) {
        if (state == null) {
            return;
        }
        for (Consumer<AssistantVisualState> listener : listeners) {
            listener.accept(state);
        }
    }
}

package com.fuad.presentation.core;

import com.fuad.pipeline.AssistantActivityListener;
import com.fuad.pipeline.AssistantActivityState;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public final class AssistantVisualStateStore implements AssistantActivityListener {
    private final AtomicReference<AssistantVisualState> state = new AtomicReference<>(AssistantVisualState.IDLE);
    private final CopyOnWriteArrayList<Consumer<AssistantVisualState>> listeners = new CopyOnWriteArrayList<>();

    @Override
    public void onStateChanged(AssistantActivityState state) {
        AssistantVisualState visualState = null;

        switch (state) {
            case IDLE -> visualState = AssistantVisualState.IDLE;
            case LISTENING -> visualState = AssistantVisualState.LISTENING;
            case PROCESSING -> visualState = AssistantVisualState.PROCESSING;
            case SPEAKING -> visualState = AssistantVisualState.SPEAKING;
        }
        set(visualState);
    }

    public AssistantVisualState current() {
        return state.get();
    }

    public void set(AssistantVisualState next) {
        Objects.requireNonNull(next);
        AssistantVisualState previous = state.getAndSet(next);

        if (previous == next) {
            return;
        }
        for (Consumer<AssistantVisualState> listener : listeners) {
            listener.accept(next);
        }
    }

    public AutoCloseable subscribe(Consumer<AssistantVisualState> listener) {
        Objects.requireNonNull(listener);

        listeners.add(listener);
        return () -> listeners.remove(listener);
    }
}

package com.fuad.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class VoiceInputController {
    private static final Logger LOGGER = LoggerFactory.getLogger(VoiceInputController.class);
    private final AtomicBoolean muted = new AtomicBoolean(false);
    private final CopyOnWriteArrayList<Consumer<Boolean>> listeners = new CopyOnWriteArrayList<>();

    public boolean isMuted() {
        return muted.get();
    }

    public void setMuted(boolean value) {
        boolean previous = muted.getAndSet(value);

        if (previous == value) {
            return;
        }
        for (Consumer<Boolean> listener : listeners) {
            try {
                listener.accept(value);
            }
            catch (RuntimeException e) {
                LOGGER.warn("Voice mute listener failed", e);
            }
        }
    }

    public AutoCloseable subscribe(Consumer<Boolean> listener) {
        Objects.requireNonNull(listener);

        listeners.add(listener);
        return () -> listeners.remove(listener);
    }
}

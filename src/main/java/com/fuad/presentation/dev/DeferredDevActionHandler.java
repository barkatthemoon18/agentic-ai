package com.fuad.presentation.dev;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class DeferredDevActionHandler implements DevActionHandler {
    private final AtomicReference<DevActionHandler> delegate = new AtomicReference<>(DevActionHandler.unavailable());

    @Override
    public boolean submit(DevActionRequest request) {
        return delegate.get().submit(request);
    }

    public void bind(DevActionHandler handler) {
        delegate.set(Objects.requireNonNull(handler));
    }
}

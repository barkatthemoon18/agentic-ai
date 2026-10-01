package com.fuad.presentation.tools;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public class DeferredMoreToolsHandler implements MoreToolsHandler {
    private final AtomicReference<MoreToolsHandler> delegate = new AtomicReference<>(MoreToolsHandler.unavailable());

    @Override
    public boolean open() {
        return delegate.get().open();
    }

    public void bind(MoreToolsHandler handler) {
        delegate.set(Objects.requireNonNull(handler));
    }
}

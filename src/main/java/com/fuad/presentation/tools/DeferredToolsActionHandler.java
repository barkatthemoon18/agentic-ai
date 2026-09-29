package com.fuad.presentation.tools;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class DeferredToolsActionHandler implements ToolsActionHandler {
    private final AtomicReference<ToolsActionHandler> delegate = new AtomicReference<>(ToolsActionHandler.unavailable());

    @Override
    public boolean submit(ToolsActionRequest request) {
        return delegate.get().submit(request);
    }

    public void bind(ToolsActionHandler handler) {
        delegate.set(Objects.requireNonNull(handler));
    }
}

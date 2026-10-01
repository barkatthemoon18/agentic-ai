package com.fuad.interaction;

import java.util.UUID;

public interface InteractionPresenter extends AutoCloseable {
    <T> void present(UUID sessionId, InteractionRequest<T> request,
                     InteractionResponder<T> responder);

    void dismiss(UUID sessionId);

    @Override
    default void close() {
    }
}

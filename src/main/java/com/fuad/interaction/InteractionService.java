package com.fuad.interaction;

import java.util.concurrent.CompletionStage;

public interface InteractionService extends AutoCloseable {
    <T> CompletionStage<InteractionResult<T>> request(InteractionRequest<T> request);

    @Override
    void close();
}

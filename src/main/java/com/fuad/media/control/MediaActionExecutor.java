package com.fuad.media.control;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public interface MediaActionExecutor {
    CompletionStage<MediaActionResult> executeAsync(MediaAction action);

    static MediaActionExecutor unavailable() { return action -> CompletableFuture
            .completedFuture(new MediaActionResult(Objects.requireNonNull(action), MediaActionStatus.CLOSED));
    }
}

package com.fuad.media.control;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public interface MediaActionExecutor {
    CompletionStage<MediaActionResult> executeAsync(MediaAction action);

    default CompletionStage<MediaActionResult> executeAsync(MediaControlRequest request) {
        Objects.requireNonNull(request);
        if (request.target() instanceof MediaTarget.Active) {
            return executeAsync(request.action());
        }
        return CompletableFuture.completedFuture(new MediaActionResult(request.action(), MediaActionStatus.REJECTED));
    }

    static MediaActionExecutor unavailable() {
        return new MediaActionExecutor() {
            @Override
            public CompletionStage<MediaActionResult> executeAsync(MediaAction action) {
                return closed(action);
            }

            @Override
            public CompletionStage<MediaActionResult> executeAsync(MediaControlRequest request) {
                Objects.requireNonNull(request);
                return closed(request.action());
            }

            private CompletionStage<MediaActionResult> closed(MediaAction action) {
                return CompletableFuture.completedFuture(new MediaActionResult(Objects.requireNonNull(action),
                        MediaActionStatus.CLOSED));
            }
        };
    }
}
package com.fuad.media.control;

import java.util.concurrent.CompletionStage;

public interface MediaActionExecutor {
    CompletionStage<MediaActionResult> executeAsync(MediaAction action);
    static MediaActionExecutor unavailable() { return null; }
}

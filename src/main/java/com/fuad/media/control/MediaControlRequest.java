package com.fuad.media.control;

import java.util.Objects;

public record MediaControlRequest(
        MediaAction action,
        MediaTarget target) {

    public MediaControlRequest {
        Objects.requireNonNull(action);
        Objects.requireNonNull(target);
    }

    public static MediaControlRequest active(MediaAction action) {
        return new MediaControlRequest(action, MediaTarget.active());
    }

    public static MediaControlRequest application(MediaAction action, String applicationName) {
        return new MediaControlRequest(action, MediaTarget.application(applicationName));
    }
}

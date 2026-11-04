package com.fuad.media.control;

import java.util.Objects;

@FunctionalInterface
public interface MediaActionHandler {
    boolean submit(MediaAction action);

    default boolean submit(MediaControlRequest request) {
        Objects.requireNonNull(request);
        if (request.target() instanceof MediaTarget.Active) {
            return submit(request.action());
        }
        return false;
    }

    static MediaActionHandler unavailable() {
        return action -> false;
    }
}

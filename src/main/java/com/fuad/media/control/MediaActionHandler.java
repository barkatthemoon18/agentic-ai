package com.fuad.media.control;

@FunctionalInterface
public interface MediaActionHandler {
    boolean submit(MediaAction action);

    static MediaActionHandler unavailable() {
        return action -> false;
    }
}

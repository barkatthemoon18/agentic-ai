package com.fuad.presentation.media;

@FunctionalInterface
public interface MediaActionHandler {
    boolean submit(MediaAction action);

    static MediaActionHandler unavailable() {
        return action -> false;
    }
}

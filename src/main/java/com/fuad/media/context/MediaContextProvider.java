package com.fuad.media.context;

@FunctionalInterface
public interface MediaContextProvider {
    MediaContextSnapshot current();

    static MediaContextProvider unavailable() {
        return MediaContextSnapshot::unavailable;
    }
}

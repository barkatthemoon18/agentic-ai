package com.fuad.media;

public interface MediaSessionProvider extends AutoCloseable {
    MediaSessionSnapshot current();
    boolean playPause();
    boolean next();
    boolean previous();

    @Override
    default void close() {
        /* TODO: Update when fully completed */
    }
}

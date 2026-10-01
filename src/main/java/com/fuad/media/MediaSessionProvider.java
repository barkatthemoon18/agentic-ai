package com.fuad.media;

public interface MediaSessionProvider extends AutoCloseable {
    MediaSessionSnapshot current();
    boolean playPause();
    boolean next();
    boolean previous();

    static MediaSessionProvider unavailable() {
        return new MediaSessionProvider() {
            @Override
            public MediaSessionSnapshot current() {
                return MediaSessionSnapshot.unavailable();
            }

            @Override
            public boolean playPause() {
                return false;
            }

            @Override
            public boolean next() {
                return false;
            }

            @Override
            public boolean previous() {
                return false;
            }
        };
    }

    @Override
    default void close() {
        /* TODO: Update when fully completed */
    }
}

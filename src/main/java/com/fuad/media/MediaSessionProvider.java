package com.fuad.media;

import com.fuad.media.control.MediaControlRequest;
import com.fuad.media.control.MediaTarget;

import java.util.Objects;
import java.util.Optional;

public interface MediaSessionProvider extends AutoCloseable {
    MediaSessionSnapshot current();
    boolean playPause();
    boolean pause();
    boolean resume();
    boolean next();
    boolean previous();

    default Optional<MediaPlaybackState> playbackState(String applicationName) {
        return Optional.empty();
    }

    default boolean execute(MediaControlRequest request) {
        Objects.requireNonNull(request);

        if (!(request.target() instanceof MediaTarget.Active)) {
            return false;
        }
        return switch (request.action()) {
            case PREVIOUS -> previous();
            case NEXT -> next();
            case PAUSE -> pause();
            case RESUME -> resume();
            case PLAY_PAUSE -> playPause();
        };
    }

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
            public boolean pause() {
                return false;
            }

            @Override
            public boolean resume() {
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
        /* Empty intentionally */
    }
}

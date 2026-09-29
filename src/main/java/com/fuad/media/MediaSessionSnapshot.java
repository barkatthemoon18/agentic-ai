package com.fuad.media;

import java.util.Objects;
import java.util.Optional;

public record MediaSessionSnapshot(
        boolean available,
        String sourceApplication,
        Optional<MediaTrack> currentTrack,
        double positionSeconds,
        MediaPlaybackState playbackState) {

    public MediaSessionSnapshot {
        sourceApplication = sourceApplication == null ? "" : sourceApplication.trim();
        currentTrack = currentTrack == null ? Optional.empty() : currentTrack;
        playbackState = Objects.requireNonNull(playbackState, "playbackState must not be null");
        positionSeconds = Math.max(0.0, positionSeconds);
    }

    public static MediaSessionSnapshot unavailable() {
        return new MediaSessionSnapshot(false, "", Optional.empty(), 0.0,
                MediaPlaybackState.STOPPED);
    }
}

package com.fuad.view.workspace.media;

public record MediaPlayerSnapshot(
        MediaTrack currentTrack,
        double positionSeconds,
        boolean playing,
        double volume,
        String outputDevice,
        String quality) {
    /* Empty intentionally */
}

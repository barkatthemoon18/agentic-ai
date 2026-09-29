package com.fuad.media;

import java.util.Optional;

public record MediaTrack(
        String title,
        String artist,
        String album,
        double durationSeconds,
        Optional<String> artwork) {

    public MediaTrack {
        artwork = artwork == null ? Optional.empty() : artwork;
    }

    public MediaTrack(String title, String artist, String album, double durationSeconds) {
        this(title, artist, album, durationSeconds, Optional.empty());
    }
}

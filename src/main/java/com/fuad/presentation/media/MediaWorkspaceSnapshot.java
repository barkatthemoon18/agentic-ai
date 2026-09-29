package com.fuad.presentation.media;

import com.fuad.media.MediaPlaybackState;
import com.fuad.media.MediaTrack;

import java.util.Optional;

public record MediaWorkspaceSnapshot(
        boolean available,
        String sourceApplication,
        Optional<MediaTrack> currentTrack,
        double positionSeconds,
        MediaPlaybackState playbackState,
        double volume,
        String outputDevice,
        String quality) {

    public MediaWorkspaceSnapshot {
        sourceApplication = sourceApplication == null ? "" : sourceApplication.trim();
        currentTrack = currentTrack == null ? Optional.empty() : currentTrack;
        playbackState = playbackState == null ? MediaPlaybackState.STOPPED : playbackState;
        positionSeconds = Math.max(0.0, positionSeconds);
        volume = Math.clamp(volume, 0.0, 1.0);
        outputDevice = outputDevice == null ? "" : outputDevice.trim();
        quality = quality == null ? "" : quality.trim();
    }

    public boolean playing() {
        return playbackState == MediaPlaybackState.PLAYING;
    }

    public String sourceDisplayName() {
        String normalized;
        String[] parts;

        if (sourceApplication == null || sourceApplication.isBlank()) {
            return "MEDIA";
        }
        normalized = sourceApplication.replaceAll("(?i)\\.exe$", "");
        parts = normalized.split("\\.");

        return parts.length == 0 ? normalized : parts[parts.length - 1];
    }

    public static MediaWorkspaceSnapshot unavailable() {
        return new MediaWorkspaceSnapshot(false, "", Optional.empty(), 0.0,
                MediaPlaybackState.STOPPED, 0.0, "", "");
    }
}

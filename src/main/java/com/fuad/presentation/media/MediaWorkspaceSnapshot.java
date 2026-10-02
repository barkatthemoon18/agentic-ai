package com.fuad.presentation.media;

import com.fuad.media.MediaPlaybackState;
import com.fuad.media.MediaTrack;
import com.fuad.media.enrichment.MediaEnrichmentSnapshot.MediaQueueItem;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

public record MediaWorkspaceSnapshot(
        boolean available,
        String sourceApplication,
        Optional<MediaTrack> currentTrack,
        double positionSeconds,
        MediaPlaybackState playbackState,
        OptionalDouble volume,
        String outputDevice,
        String quality,
        List<MediaQueueItem> queue,
        Optional<MediaPlaybackState> mediaPlaybackState) {

    public MediaWorkspaceSnapshot {
        sourceApplication = sourceApplication == null ? "" : sourceApplication.trim();
        currentTrack = currentTrack == null ? Optional.empty() : currentTrack;
        playbackState = playbackState == null ? MediaPlaybackState.STOPPED : playbackState;
        positionSeconds = Math.max(0.0, positionSeconds);
        Objects.requireNonNull(volume, "Volume must not be null");
        outputDevice = outputDevice == null ? "" : outputDevice.trim();
        quality = quality == null ? "" : quality.trim();
        queue = queue == null ? List.of() : List.copyOf(queue);
        mediaPlaybackState = mediaPlaybackState == null ? Optional.empty() : mediaPlaybackState;
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
                MediaPlaybackState.STOPPED, OptionalDouble.empty(), "", "", List.of(), null);
    }
}

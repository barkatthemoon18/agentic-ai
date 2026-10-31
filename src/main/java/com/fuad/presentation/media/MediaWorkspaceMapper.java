package com.fuad.presentation.media;

import com.fuad.audio.output.AudioOutputSnapshot;
import com.fuad.media.MediaPlaybackState;
import com.fuad.media.MediaSessionSnapshot;
import com.fuad.media.context.MediaContextSnapshot;
import com.fuad.media.enrichment.MediaEnrichmentSnapshot;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

public final class MediaWorkspaceMapper {
    private static final Duration CORE_MAX_AGE = Duration.ofSeconds(2);
    private static final Duration ENRICHMENT_MAX_AGE = Duration.ofSeconds(5);
    private final Clock clock;

    public MediaWorkspaceMapper() {
        this(Clock.systemUTC());
    }

    public MediaWorkspaceMapper(Clock clock) {
        this.clock = Objects.requireNonNull(clock);
    }

    public MediaWorkspaceSnapshot map(MediaContextSnapshot context) {
        Optional<MediaPlaybackState> tidalPlayback;
        MediaSessionSnapshot session;
        AudioOutputSnapshot audio;
        MediaEnrichmentSnapshot enrichment;
        boolean tidalActive;

        Objects.requireNonNull(context);
        session = context.activeSession().isFresh(clock, CORE_MAX_AGE) ?
                context.activeSession().value() : MediaSessionSnapshot.unavailable();
        audio = context.audioOutput().isFresh(clock, CORE_MAX_AGE) ?
                context.audioOutput().value() : AudioOutputSnapshot.unavailable();
        tidalPlayback = context.tidalPlayback().isFresh(clock, CORE_MAX_AGE) ?
                context.tidalPlayback().value() : Optional.empty();
        tidalActive = session.available() && isTidal(session.sourceApplication());
        enrichment = tidalActive && context.tidalEnrichment().isFresh(clock, ENRICHMENT_MAX_AGE) ?
                context.tidalEnrichment().value() : MediaEnrichmentSnapshot.unavailable();
        return new MediaWorkspaceSnapshot(session.available(), session.sourceApplication(), session.currentTrack(),
                session.positionSeconds(), session.playbackState(), audio.volume(), audio.deviceName(),
                enrichment.quality(), enrichment.queue(), tidalPlayback);
    }

    private static boolean isTidal(String application) {
        if (application == null || application.isBlank()) {
            return false;
        }
        return application.trim().replaceFirst("(?i)\\.exe$", "").equalsIgnoreCase("TIDAL");
    }
}

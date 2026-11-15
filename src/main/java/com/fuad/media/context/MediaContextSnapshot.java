package com.fuad.media.context;

import com.fuad.audio.output.AudioOutputSnapshot;
import com.fuad.media.MediaPlaybackState;
import com.fuad.media.MediaSessionSnapshot;
import com.fuad.media.enrichment.MediaEnrichmentSnapshot;

import java.util.Objects;
import java.util.Optional;

public record MediaContextSnapshot(
        MediaSample<MediaSessionSnapshot> activeSession,
        MediaSample<Optional<MediaPlaybackState>> tidalPlayback,
        MediaSample<MediaEnrichmentSnapshot> tidalEnrichment,
        MediaSample<AudioOutputSnapshot> audioOutput,
        MediaSample<ProcessPresence> tidalProcess) {

    public MediaContextSnapshot {
        Objects.requireNonNull(activeSession);
        Objects.requireNonNull(tidalPlayback);
        Objects.requireNonNull(tidalEnrichment);
        Objects.requireNonNull(audioOutput);
        Objects.requireNonNull(tidalProcess);
    }

    public static MediaContextSnapshot unavailable() {
        return new MediaContextSnapshot(MediaSample.unavailable(MediaSessionSnapshot.unavailable()),
                MediaSample.unavailable(Optional.empty()),
                MediaSample.unavailable(MediaEnrichmentSnapshot.unavailable()),
                MediaSample.unavailable(AudioOutputSnapshot.unavailable()),
                MediaSample.unavailable(ProcessPresence.UNKNOWN));
    }
}

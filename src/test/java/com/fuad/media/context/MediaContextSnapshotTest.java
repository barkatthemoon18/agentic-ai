package com.fuad.media.context;

import com.fuad.audio.output.AudioOutputSnapshot;
import com.fuad.media.MediaPlaybackState;
import com.fuad.media.MediaSessionSnapshot;
import com.fuad.media.enrichment.MediaEnrichmentSnapshot;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class MediaContextSnapshotTest {
    @Test
    void unavailableSnapshotShouldProvideUnavailableSamples() {
        MediaContextSnapshot snapshot = MediaContextSnapshot.unavailable();

        assertNotNull(snapshot.activeSession());
        assertNotNull(snapshot.tidalPlayback());
        assertNotNull(snapshot.tidalEnrichment());
        assertNotNull(snapshot.audioOutput());
        assertNotNull(snapshot.tidalProcess());

        assertFalse(snapshot.activeSession().readSucceeded());
        assertFalse(snapshot.tidalPlayback().readSucceeded());
        assertFalse(snapshot.tidalEnrichment().readSucceeded());
        assertFalse(snapshot.audioOutput().readSucceeded());
        assertFalse(snapshot.tidalProcess().readSucceeded());

        assertEquals(ProcessPresence.UNKNOWN, snapshot.tidalProcess().value());
        assertEquals(Optional.empty(), snapshot.tidalPlayback().value());
    }

    @Test
    void constructorShouldEnforceNonNullComponents() {
        var sampleSession = MediaSample.unavailable(MediaSessionSnapshot.unavailable());
        var samplePlayback = MediaSample.unavailable(Optional.<MediaPlaybackState>empty());
        var sampleEnrichment = MediaSample.unavailable(MediaEnrichmentSnapshot.unavailable());
        var sampleAudio = MediaSample.unavailable(AudioOutputSnapshot.unavailable());
        var sampleProcess = MediaSample.unavailable(ProcessPresence.UNKNOWN);

        assertThrows(NullPointerException.class, () -> new MediaContextSnapshot(null, samplePlayback, sampleEnrichment, sampleAudio, sampleProcess));
        assertThrows(NullPointerException.class, () -> new MediaContextSnapshot(sampleSession, null, sampleEnrichment, sampleAudio, sampleProcess));
        assertThrows(NullPointerException.class, () -> new MediaContextSnapshot(sampleSession, samplePlayback, null, sampleAudio, sampleProcess));
        assertThrows(NullPointerException.class, () -> new MediaContextSnapshot(sampleSession, samplePlayback, sampleEnrichment, null, sampleProcess));
        assertThrows(NullPointerException.class, () -> new MediaContextSnapshot(sampleSession, samplePlayback, sampleEnrichment, sampleAudio, null));
    }
}

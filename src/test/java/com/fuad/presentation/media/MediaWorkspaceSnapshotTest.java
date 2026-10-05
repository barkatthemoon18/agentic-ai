package com.fuad.presentation.media;

import com.fuad.media.MediaPlaybackState;
import com.fuad.media.MediaTrack;
import com.fuad.media.enrichment.MediaEnrichmentSnapshot;
import com.fuad.media.enrichment.MediaEnrichmentSnapshot.MediaQueueItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.*;

class MediaWorkspaceSnapshotTest {
    @Test
    void shouldNormalizeMissingMetadataWithoutInventingVolumeOrPlayback() {
        MediaWorkspaceSnapshot snapshot = new MediaWorkspaceSnapshot(true, null, null, -5,
                null, OptionalDouble.empty(), null, null, null, null);

        assertEquals("", snapshot.sourceApplication());
        assertEquals("MEDIA", snapshot.sourceDisplayName());
        assertTrue(snapshot.currentTrack().isEmpty());
        assertEquals(0, snapshot.positionSeconds());
        assertEquals(MediaPlaybackState.STOPPED, snapshot.playbackState());
        assertFalse(snapshot.playing());
        assertTrue(snapshot.volume().isEmpty());
        assertEquals("", snapshot.outputDevice());
        assertEquals("", snapshot.quality());
        assertTrue(snapshot.queue().isEmpty());
        assertTrue(snapshot.mediaPlaybackState().isEmpty());
        assertThrows(NullPointerException.class, () -> new MediaWorkspaceSnapshot(false, "", null,
                0, null, null, "", "", null, null));
    }

    @ParameterizedTest
    @CsvSource({"' TIDAL.exe ',TIDAL", "Tidal.EXE,Tidal", "com.vendor.Tidal,Tidal",
            "Spotify,Spotify", "' ',MEDIA"})
    void shouldDisplayApplicationNameWithoutNamespaceOrExecutableSuffix(String source, String expected) {
        MediaWorkspaceSnapshot snapshot = new MediaWorkspaceSnapshot(true, source, null, 0,
                null, OptionalDouble.empty(), "", "", null, null);
        assertEquals(expected, snapshot.sourceDisplayName());
    }

    @ParameterizedTest
    @EnumSource(MediaPlaybackState.class)
    void activeSessionPlaybackShouldBeIndependentOfTidalPlayback(MediaPlaybackState state) {
        MediaTrack track = new MediaTrack("Song", "Artist", "Album", 120);
        MediaWorkspaceSnapshot snapshot = new MediaWorkspaceSnapshot(true, "Spotify", Optional.of(track),
                15.5, state, OptionalDouble.of(0.25), " Speakers ", " 24-bit 96 kHz ",
                List.of(), Optional.of(MediaPlaybackState.PAUSED));

        assertEquals(state == MediaPlaybackState.PLAYING, snapshot.playing());
        assertEquals(Optional.of(MediaPlaybackState.PAUSED), snapshot.mediaPlaybackState());
        assertSame(track, snapshot.currentTrack().orElseThrow());
        assertEquals(15.5, snapshot.positionSeconds());
        assertEquals(0.25, snapshot.volume().orElseThrow());
        assertEquals("Speakers", snapshot.outputDevice());
        assertEquals("24-bit 96 kHz", snapshot.quality());
    }

    @Test
    void queueAndArtistsShouldBeImmutableAndNormalized() {
        List<String> artists = new ArrayList<>();
        artists.add(null);
        artists.add("  ");
        artists.add(" Artist ");
        artists.add("Artist");
        artists.add("Guest");
        MediaQueueItem item = new MediaQueueItem(" Song ", artists);
        List<MediaQueueItem> queue = new ArrayList<>(List.of(item));
        MediaEnrichmentSnapshot enrichment = new MediaEnrichmentSnapshot(" LOSSLESS ", queue);
        MediaWorkspaceSnapshot snapshot = new MediaWorkspaceSnapshot(true, "Tidal", null, 0,
                null, OptionalDouble.empty(), "", enrichment.quality(), queue, null);
        queue.clear();
        artists.clear();

        assertEquals(List.of(item), snapshot.queue());
        assertEquals(List.of(item), enrichment.queue());
        assertEquals("Song", item.title());
        assertEquals("Artist, Guest", item.displayArtist());
        assertEquals("LOSSLESS", snapshot.quality());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.queue().clear());
        assertThrows(UnsupportedOperationException.class, () -> item.artists().clear());
    }

    @Test
    void unavailableShouldRepresentNoSessionOrDevice() {
        MediaWorkspaceSnapshot snapshot = MediaWorkspaceSnapshot.unavailable();
        assertFalse(snapshot.available());
        assertFalse(snapshot.playing());
        assertTrue(snapshot.currentTrack().isEmpty());
        assertTrue(snapshot.volume().isEmpty());
        assertTrue(snapshot.mediaPlaybackState().isEmpty());
        assertTrue(snapshot.queue().isEmpty());
    }
}

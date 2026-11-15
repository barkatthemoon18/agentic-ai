package com.fuad.presentation.media;

import com.fuad.audio.output.AudioOutputSnapshot;
import com.fuad.media.MediaPlaybackState;
import com.fuad.media.MediaSessionSnapshot;
import com.fuad.media.MediaTrack;
import com.fuad.media.context.MediaContextSnapshot;
import com.fuad.media.context.MediaSample;
import com.fuad.media.context.ProcessPresence;
import com.fuad.media.enrichment.MediaEnrichmentSnapshot;
import com.fuad.media.enrichment.MediaEnrichmentSnapshot.MediaQueueItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.*;

class MediaWorkspaceMapperTest {
    private static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private final MediaWorkspaceMapper mapper = new MediaWorkspaceMapper(CLOCK);
    private final MediaTrack track = new MediaTrack("Song", "Artist", "Album", 120);

    private static <T> MediaSample<T> sample(T value, Duration age) {
        return new MediaSample<>(value, NOW.minus(age), true);
    }

    private MediaSessionSnapshot session(String source) {
        return new MediaSessionSnapshot(true, source, Optional.of(track), 20, MediaPlaybackState.PLAYING);
    }

    private static AudioOutputSnapshot audio() {
        return new AudioOutputSnapshot(true, "endpoint", "Speakers", OptionalDouble.of(0.5), true);
    }

    private static MediaEnrichmentSnapshot enrichment() {
        return new MediaEnrichmentSnapshot("LOSSLESS", List.of(new MediaQueueItem("Next song", List.of("Guest"))));
    }

    private MediaContextSnapshot context(String source, Duration coreAge, Duration enrichmentAge) {
        return new MediaContextSnapshot(sample(session(source), coreAge),
                sample(Optional.of(MediaPlaybackState.PAUSED), coreAge), sample(enrichment(), enrichmentAge),
                sample(audio(), coreAge), sample(ProcessPresence.RUNNING, coreAge));
    }

    @Test
    void unavailableContextShouldMapToTheUnavailableWorkspace() {
        MediaWorkspaceSnapshot snapshot = mapper.map(MediaContextSnapshot.unavailable());

        assertFalse(snapshot.available());
        assertTrue(snapshot.volume().isEmpty());
        assertEquals("", snapshot.quality());
        assertTrue(snapshot.queue().isEmpty());
        assertTrue(snapshot.mediaPlaybackState().isEmpty());
    }

    @Test
    void freshContextShouldMapEveryProvider() {
        MediaWorkspaceSnapshot snapshot = mapper.map(context("TIDAL", Duration.ofMillis(100), Duration.ofSeconds(1)));

        assertTrue(snapshot.available());
        assertSame(track, snapshot.currentTrack().orElseThrow());
        assertEquals(20, snapshot.positionSeconds());
        assertEquals(0.5, snapshot.volume().orElseThrow());
        assertEquals("Speakers", snapshot.outputDevice());
        assertEquals(Optional.of(MediaPlaybackState.PAUSED), snapshot.mediaPlaybackState());
        assertEquals("LOSSLESS", snapshot.quality());
        assertEquals("Next song", snapshot.queue().getFirst().title());
    }

    @ParameterizedTest
    @ValueSource(strings = {"TIDAL", "Tidal", "tidal.exe", " TIDAL.EXE "})
    void enrichmentShouldBeExposedOnlyForTidalSessions(String source) {
        assertEquals("LOSSLESS", mapper.map(context(source, Duration.ZERO, Duration.ZERO)).quality());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Spotify", "", "TIDAL Connect", "NotTIDAL.exe"})
    void enrichmentShouldBeHiddenForNonTidalSessions(String source) {
        MediaWorkspaceSnapshot snapshot = mapper.map(context(source, Duration.ZERO, Duration.ZERO));

        assertEquals("", snapshot.quality());
        assertTrue(snapshot.queue().isEmpty());
        // Independent TIDAL playback state is not gated by the active session.
        assertEquals(Optional.of(MediaPlaybackState.PAUSED), snapshot.mediaPlaybackState());
    }

    @Test
    void staleCoreSamplesShouldBeDroppedAfterTwoSeconds() {
        MediaWorkspaceSnapshot snapshot = mapper.map(context("TIDAL", Duration.ofMillis(2001), Duration.ZERO));

        assertFalse(snapshot.available());
        assertTrue(snapshot.volume().isEmpty());
        assertTrue(snapshot.mediaPlaybackState().isEmpty());
        // Without a fresh active TIDAL session the enrichment is not trusted either.
        assertEquals("", snapshot.quality());
    }

    @Test
    void coreSamplesExactlyAtTheLimitShouldStillBeFresh() {
        assertTrue(mapper.map(context("Spotify", Duration.ofSeconds(2), Duration.ZERO)).available());
    }

    @Test
    void staleEnrichmentShouldBeDroppedAfterFiveSecondsWithoutAffectingCore() {
        MediaWorkspaceSnapshot fresh = mapper.map(context("TIDAL", Duration.ZERO, Duration.ofSeconds(5)));
        MediaWorkspaceSnapshot stale = mapper.map(context("TIDAL", Duration.ZERO, Duration.ofMillis(5001)));

        assertEquals("LOSSLESS", fresh.quality());
        assertEquals("", stale.quality());
        assertTrue(stale.queue().isEmpty());
        assertTrue(stale.available());
        assertTrue(stale.volume().isPresent());
    }

    @Test
    void failedReadsShouldBeTreatedAsUnavailableEvenWhenTimestampsAreFresh() {
        MediaContextSnapshot fresh = context("TIDAL", Duration.ZERO, Duration.ZERO);
        MediaContextSnapshot failed = new MediaContextSnapshot(fresh.activeSession().asFailed(),
                fresh.tidalPlayback().asFailed(), fresh.tidalEnrichment().asFailed(), fresh.audioOutput().asFailed(),
                fresh.tidalProcess().asFailed());

        MediaWorkspaceSnapshot snapshot = mapper.map(failed);

        assertFalse(snapshot.available());
        assertTrue(snapshot.volume().isEmpty());
        assertEquals("", snapshot.quality());
        assertTrue(snapshot.mediaPlaybackState().isEmpty());
    }

    @Test
    void shouldRejectNullDependencies() {
        assertThrows(NullPointerException.class, () -> new MediaWorkspaceMapper(null));
        assertThrows(NullPointerException.class, () -> mapper.map(null));
    }
}

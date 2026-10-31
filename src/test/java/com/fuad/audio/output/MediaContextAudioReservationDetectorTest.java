package com.fuad.audio.output;

import com.fuad.media.MediaPlaybackState;
import com.fuad.media.MediaSessionSnapshot;
import com.fuad.media.context.MediaContextProvider;
import com.fuad.media.context.MediaContextSnapshot;
import com.fuad.media.context.MediaSample;
import com.fuad.media.context.ProcessPresence;
import com.fuad.media.enrichment.MediaEnrichmentSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MediaContextAudioReservationDetectorTest {
    private final Instant baseTime = Instant.parse("2026-10-31T12:00:00Z");
    private final Clock clock = Clock.fixed(baseTime, ZoneOffset.UTC);
    private MediaContextProvider contextProvider;

    @BeforeEach
    void setUp() {
        contextProvider = mock(MediaContextProvider.class);
    }

    @Test
    void constructorShouldValidateArguments() {
        assertThrows(NullPointerException.class, () -> new MediaContextAudioReservationDetector(null, clock));
        assertThrows(NullPointerException.class, () -> new MediaContextAudioReservationDetector(contextProvider, null));
        assertDoesNotThrow(() -> new MediaContextAudioReservationDetector(contextProvider));
    }

    @Test
    void providerExceptionShouldYieldUnknownReservationAndBlockTts() {
        when(contextProvider.current()).thenThrow(new RuntimeException("Crash"));
        MediaContextAudioReservationDetector detector = new MediaContextAudioReservationDetector(contextProvider, clock);

        assertEquals(AudioReservation.UNKNOWN, detector.reservation());
        assertTrue(detector.isOutputReserved());
    }

    @Test
    void staleOrFailedProcessSampleShouldYieldUnknownReservation() {
        MediaContextSnapshot snapshot = new MediaContextSnapshot(
                MediaSample.unavailable(MediaSessionSnapshot.unavailable()),
                MediaSample.unavailable(Optional.empty()),
                MediaSample.unavailable(MediaEnrichmentSnapshot.unavailable()),
                MediaSample.unavailable(AudioOutputSnapshot.unavailable()),
                MediaSample.unavailable(ProcessPresence.RUNNING) // readSucceeded is false
        );
        when(contextProvider.current()).thenReturn(snapshot);

        MediaContextAudioReservationDetector detector = new MediaContextAudioReservationDetector(contextProvider, clock);
        assertEquals(AudioReservation.UNKNOWN, detector.reservation());
        assertTrue(detector.isOutputReserved());

        // Process sampled more than 5 seconds ago
        Clock expiredClock = Clock.fixed(baseTime.plusSeconds(6), ZoneOffset.UTC);
        MediaContextSnapshot expiredSnapshot = new MediaContextSnapshot(
                MediaSample.unavailable(MediaSessionSnapshot.unavailable()),
                MediaSample.unavailable(Optional.empty()),
                MediaSample.unavailable(MediaEnrichmentSnapshot.unavailable()),
                MediaSample.unavailable(AudioOutputSnapshot.unavailable()),
                MediaSample.observed(ProcessPresence.RUNNING, clock)
        );
        when(contextProvider.current()).thenReturn(expiredSnapshot);
        MediaContextAudioReservationDetector expiredDetector = new MediaContextAudioReservationDetector(contextProvider, expiredClock);
        assertEquals(AudioReservation.UNKNOWN, expiredDetector.reservation());
        assertTrue(expiredDetector.isOutputReserved());
    }

    @Test
    void runningProcessShouldBePotentiallyReserved() {
        MediaContextSnapshot snapshot = new MediaContextSnapshot(
                MediaSample.unavailable(MediaSessionSnapshot.unavailable()),
                MediaSample.unavailable(Optional.empty()),
                MediaSample.unavailable(MediaEnrichmentSnapshot.unavailable()),
                MediaSample.unavailable(AudioOutputSnapshot.unavailable()),
                MediaSample.observed(ProcessPresence.RUNNING, clock)
        );
        when(contextProvider.current()).thenReturn(snapshot);

        MediaContextAudioReservationDetector detector = new MediaContextAudioReservationDetector(contextProvider, clock);
        assertEquals(AudioReservation.POTENTIALLY_RESERVED, detector.reservation());
        assertTrue(detector.isOutputReserved());
    }

    @Test
    void unknownProcessShouldYieldUnknownReservation() {
        MediaContextSnapshot snapshot = new MediaContextSnapshot(
                MediaSample.unavailable(MediaSessionSnapshot.unavailable()),
                MediaSample.unavailable(Optional.empty()),
                MediaSample.unavailable(MediaEnrichmentSnapshot.unavailable()),
                MediaSample.unavailable(AudioOutputSnapshot.unavailable()),
                MediaSample.observed(ProcessPresence.UNKNOWN, clock)
        );
        when(contextProvider.current()).thenReturn(snapshot);

        MediaContextAudioReservationDetector detector = new MediaContextAudioReservationDetector(contextProvider, clock);
        assertEquals(AudioReservation.UNKNOWN, detector.reservation());
        assertTrue(detector.isOutputReserved());
    }

    @ParameterizedTest
    @CsvSource({
            "PAUSED, AVAILABLE, false",
            "STOPPED, AVAILABLE, false"
    })
    void notRunningProcessWithNonPlayingPlaybackShouldBeAvailable(String state, AudioReservation expectedRes, boolean expectedBlock) {
        MediaContextSnapshot snapshot = new MediaContextSnapshot(
                MediaSample.unavailable(MediaSessionSnapshot.unavailable()),
                MediaSample.observed(Optional.of(MediaPlaybackState.valueOf(state)), clock),
                MediaSample.unavailable(MediaEnrichmentSnapshot.unavailable()),
                MediaSample.unavailable(AudioOutputSnapshot.unavailable()),
                MediaSample.observed(ProcessPresence.NOT_RUNNING, clock)
        );
        when(contextProvider.current()).thenReturn(snapshot);

        MediaContextAudioReservationDetector detector = new MediaContextAudioReservationDetector(contextProvider, clock);
        assertEquals(expectedRes, detector.reservation());
        assertEquals(expectedBlock, detector.isOutputReserved());
    }

    @Test
    void notRunningProcessWithContradictoryPlayingPlaybackShouldYieldUnknown() {
        // Process says NOT_RUNNING, but fresh playback says PLAYING -> contradiction -> UNKNOWN
        MediaContextSnapshot snapshot = new MediaContextSnapshot(
                MediaSample.unavailable(MediaSessionSnapshot.unavailable()),
                MediaSample.observed(Optional.of(MediaPlaybackState.PLAYING), clock),
                MediaSample.unavailable(MediaEnrichmentSnapshot.unavailable()),
                MediaSample.unavailable(AudioOutputSnapshot.unavailable()),
                MediaSample.observed(ProcessPresence.NOT_RUNNING, clock)
        );
        when(contextProvider.current()).thenReturn(snapshot);

        MediaContextAudioReservationDetector detector = new MediaContextAudioReservationDetector(contextProvider, clock);
        assertEquals(AudioReservation.UNKNOWN, detector.reservation());
        assertTrue(detector.isOutputReserved());
    }

    @Test
    void notRunningProcessWithStalePlayingPlaybackShouldIgnoreContradictionAndBeAvailable() {
        // Playback was recorded at baseTime, but clock is 3 seconds later (> PLAYBACK_MAX_AGE of 2s)
        Clock futureClock = Clock.fixed(baseTime.plusSeconds(3), ZoneOffset.UTC);
        MediaContextSnapshot snapshot = new MediaContextSnapshot(
                MediaSample.unavailable(MediaSessionSnapshot.unavailable()),
                MediaSample.observed(Optional.of(MediaPlaybackState.PLAYING), clock),
                MediaSample.observed(MediaEnrichmentSnapshot.unavailable(), futureClock),
                MediaSample.observed(AudioOutputSnapshot.unavailable(), futureClock),
                MediaSample.observed(ProcessPresence.NOT_RUNNING, futureClock)
        );
        when(contextProvider.current()).thenReturn(snapshot);

        MediaContextAudioReservationDetector detector = new MediaContextAudioReservationDetector(contextProvider, futureClock);
        assertEquals(AudioReservation.AVAILABLE, detector.reservation());
        assertFalse(detector.isOutputReserved());
    }
}

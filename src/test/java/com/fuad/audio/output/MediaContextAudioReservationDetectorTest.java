package com.fuad.audio.output;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fuad.logging.LogCategory;
import com.fuad.media.MediaPlaybackState;
import com.fuad.media.MediaSessionSnapshot;
import com.fuad.media.context.MediaContextProvider;
import com.fuad.media.context.MediaContextSnapshot;
import com.fuad.media.context.MediaSample;
import com.fuad.media.context.ProcessPresence;
import com.fuad.media.enrichment.MediaEnrichmentSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MediaContextAudioReservationDetectorTest {
    private final Instant baseTime = Instant.parse("2026-10-31T12:00:00Z");
    private final Clock clock = Clock.fixed(baseTime, ZoneOffset.UTC);
    private MediaContextProvider contextProvider;

    private Logger logger;
    private Level originalLevel;
    private ListAppender<ILoggingEvent> capturedEvents;

    @BeforeEach
    void setUp() {
        contextProvider = mock(MediaContextProvider.class);
        logger = (Logger) LoggerFactory.getLogger(MediaContextAudioReservationDetector.class);
        originalLevel = logger.getLevel();
        logger.setLevel(Level.INFO);
        capturedEvents = new ListAppender<>();
        capturedEvents.start();
        logger.addAppender(capturedEvents);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(capturedEvents);
        capturedEvents.stop();
        logger.setLevel(originalLevel);
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

    @Test
    void shouldNotDuplicateLogsForIdenticalReservationStates() {
        MediaContextSnapshot availableSnapshot = new MediaContextSnapshot(
                MediaSample.unavailable(MediaSessionSnapshot.unavailable()),
                MediaSample.observed(Optional.of(MediaPlaybackState.PAUSED), clock),
                MediaSample.unavailable(MediaEnrichmentSnapshot.unavailable()),
                MediaSample.unavailable(AudioOutputSnapshot.unavailable()),
                MediaSample.observed(ProcessPresence.NOT_RUNNING, clock)
        );
        MediaContextSnapshot potentiallyReservedSnapshot = new MediaContextSnapshot(
                MediaSample.unavailable(MediaSessionSnapshot.unavailable()),
                MediaSample.unavailable(Optional.empty()),
                MediaSample.unavailable(MediaEnrichmentSnapshot.unavailable()),
                MediaSample.unavailable(AudioOutputSnapshot.unavailable()),
                MediaSample.observed(ProcessPresence.RUNNING, clock)
        );
        MediaContextSnapshot unknownSnapshot = new MediaContextSnapshot(
                MediaSample.unavailable(MediaSessionSnapshot.unavailable()),
                MediaSample.unavailable(Optional.empty()),
                MediaSample.unavailable(MediaEnrichmentSnapshot.unavailable()),
                MediaSample.unavailable(AudioOutputSnapshot.unavailable()),
                MediaSample.observed(ProcessPresence.UNKNOWN, clock)
        );

        when(contextProvider.current()).thenReturn(availableSnapshot);
        MediaContextAudioReservationDetector detector = new MediaContextAudioReservationDetector(contextProvider, clock);

        // Multiple calls with the same state (AVAILABLE)
        assertEquals(AudioReservation.AVAILABLE, detector.reservation());
        assertEquals(AudioReservation.AVAILABLE, detector.reservation());
        assertEquals(AudioReservation.AVAILABLE, detector.reservation());

        // Zero logs for identical states
        List<ILoggingEvent> reservationLogs = capturedEvents.list.stream()
                .filter(event -> event.getFormattedMessage().contains("event=MEDIA_RESERVATION_CHANGED"))
                .toList();
        assertTrue(reservationLogs.isEmpty());

        // State changes to POTENTIALLY_RESERVED
        when(contextProvider.current()).thenReturn(potentiallyReservedSnapshot);
        assertEquals(AudioReservation.POTENTIALLY_RESERVED, detector.reservation());
        // Subsequent calls with same state: no duplicate logs
        assertEquals(AudioReservation.POTENTIALLY_RESERVED, detector.reservation());
        assertEquals(AudioReservation.POTENTIALLY_RESERVED, detector.reservation());

        reservationLogs = capturedEvents.list.stream()
                .filter(event -> event.getFormattedMessage().contains("event=MEDIA_RESERVATION_CHANGED"))
                .toList();
        assertEquals(1, reservationLogs.size());
        ILoggingEvent firstChange = reservationLogs.getFirst();
        assertEquals("event=MEDIA_RESERVATION_CHANGED from=AVAILABLE to=POTENTIALLY_RESERVED",
                firstChange.getFormattedMessage());
        assertEquals(Level.INFO, firstChange.getLevel());
        assertEquals(List.of(LogCategory.MEDIA), firstChange.getMarkerList());

        // State changes to UNKNOWN
        when(contextProvider.current()).thenReturn(unknownSnapshot);
        assertEquals(AudioReservation.UNKNOWN, detector.reservation());
        // Subsequent calls with same state: no duplicate logs
        assertEquals(AudioReservation.UNKNOWN, detector.reservation());
        assertEquals(AudioReservation.UNKNOWN, detector.reservation());

        reservationLogs = capturedEvents.list.stream()
                .filter(event -> event.getFormattedMessage().contains("event=MEDIA_RESERVATION_CHANGED"))
                .toList();
        assertEquals(2, reservationLogs.size());
        ILoggingEvent secondChange = reservationLogs.get(1);
        assertEquals("event=MEDIA_RESERVATION_CHANGED from=POTENTIALLY_RESERVED to=UNKNOWN",
                secondChange.getFormattedMessage());
        assertEquals(Level.INFO, secondChange.getLevel());
        assertEquals(List.of(LogCategory.MEDIA), secondChange.getMarkerList());
    }
}

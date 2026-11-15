package com.fuad.media.context;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class MediaSampleTest {
    private final Instant baseTime = Instant.parse("2026-10-31T12:00:00Z");
    private final Clock clock = Clock.fixed(baseTime, ZoneOffset.UTC);

    @Test
    void nullArgumentsShouldBeRejected() {
        assertThrows(NullPointerException.class, () -> new MediaSample<>(null, baseTime, true));
        assertThrows(NullPointerException.class, () -> new MediaSample<>("value", null, true));
        assertThrows(NullPointerException.class, () -> MediaSample.unavailable(null));
        assertThrows(NullPointerException.class, () -> MediaSample.observed(null, clock));
        assertThrows(NullPointerException.class, () -> MediaSample.observed("value", null));
    }

    @Test
    void unavailableSampleShouldHaveEpochAndFailedStatus() {
        MediaSample<String> sample = MediaSample.unavailable("default");

        assertEquals("default", sample.value());
        assertEquals(Instant.EPOCH, sample.sampledAt());
        assertFalse(sample.readSucceeded());
        assertFalse(sample.isFresh(clock, Duration.ofSeconds(10)));
    }

    @Test
    void observedSampleShouldHaveClockInstantAndSuccessStatus() {
        MediaSample<String> sample = MediaSample.observed("valid", clock);

        assertEquals("valid", sample.value());
        assertEquals(baseTime, sample.sampledAt());
        assertTrue(sample.readSucceeded());
        assertTrue(sample.isFresh(clock, Duration.ofSeconds(1)));
    }

    @Test
    void asFailedShouldPreserveValueAndTimestampWhileMarkingFailed() {
        MediaSample<String> observed = MediaSample.observed("valid", clock);
        MediaSample<String> failed = observed.asFailed();

        assertEquals("valid", failed.value());
        assertEquals(baseTime, failed.sampledAt());
        assertFalse(failed.readSucceeded());
        assertFalse(failed.isFresh(clock, Duration.ofSeconds(10)));
    }

    @Test
    void freshnessShouldValidateWindowAndArguments() {
        MediaSample<String> sample = MediaSample.observed("valid", clock);

        assertThrows(NullPointerException.class, () -> sample.isFresh(null, Duration.ofSeconds(5)));
        assertThrows(NullPointerException.class, () -> sample.isFresh(clock, null));
        assertThrows(IllegalArgumentException.class, () -> sample.isFresh(clock, Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> sample.isFresh(clock, Duration.ofSeconds(-1)));

        Clock withinAge = Clock.fixed(baseTime.plusSeconds(4), ZoneOffset.UTC);
        assertTrue(sample.isFresh(withinAge, Duration.ofSeconds(5)));

        Clock atLimit = Clock.fixed(baseTime.plusSeconds(5), ZoneOffset.UTC);
        assertTrue(sample.isFresh(atLimit, Duration.ofSeconds(5)));

        Clock expired = Clock.fixed(baseTime.plusSeconds(6), ZoneOffset.UTC);
        assertFalse(sample.isFresh(expired, Duration.ofSeconds(5)));

        Clock beforeSampled = Clock.fixed(baseTime.minusSeconds(1), ZoneOffset.UTC);
        assertFalse(sample.isFresh(beforeSampled, Duration.ofSeconds(5)));
    }
}

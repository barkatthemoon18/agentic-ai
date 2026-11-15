package com.fuad.media.context;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record MediaSample<T>(
        T value,
        Instant sampledAt,
        boolean readSucceeded) {

    public MediaSample {
        Objects.requireNonNull(value);
        Objects.requireNonNull(sampledAt);
    }

    public static <T> MediaSample<T> unavailable(T fallback) {
        return new MediaSample<>(Objects.requireNonNull(fallback), Instant.EPOCH, false);
    }

    public static <T> MediaSample<T> observed(T value, Clock clock) {
        Objects.requireNonNull(clock);

        return new MediaSample<>(Objects.requireNonNull(value), clock.instant(), true);
    }

    public MediaSample<T> asFailed() {
        return new MediaSample<>(value, sampledAt, false);
    }

    public boolean isFresh(Clock clock, Duration maxAge) {
        Instant now;

        Objects.requireNonNull(clock);
        Objects.requireNonNull(maxAge);
        if (maxAge.isNegative() || maxAge.isZero()) {
            throw new IllegalArgumentException("maxAge must be positive");
        }
        now = clock.instant();
        return readSucceeded && !now.isBefore(sampledAt) && !now.isAfter(sampledAt.plus(maxAge));
    }
}

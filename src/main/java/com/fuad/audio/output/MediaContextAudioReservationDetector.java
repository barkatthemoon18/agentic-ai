package com.fuad.audio.output;

import com.fuad.logging.LogCategory;
import com.fuad.media.MediaPlaybackState;
import com.fuad.media.context.MediaContextProvider;
import com.fuad.media.context.MediaContextSnapshot;
import com.fuad.media.context.MediaSample;
import com.fuad.media.context.ProcessPresence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import static com.fuad.logging.LogCategory.MEDIA;

public final class MediaContextAudioReservationDetector implements AudioReservationDetector {
    private static final Logger LOGGER = LoggerFactory.getLogger(MediaContextAudioReservationDetector.class);
    private static final Duration PROCESS_MAX_AGE = Duration.ofSeconds(5);
    private static final Duration PLAYBACK_MAX_AGE = Duration.ofSeconds(2);
    private final AtomicReference<AudioReservation> lastReservation = new AtomicReference<>();
    private final MediaContextProvider contextProvider;
    private final Clock clock;

    public MediaContextAudioReservationDetector(MediaContextProvider contextProvider) {
        this(contextProvider, Clock.systemUTC());
    }

    public MediaContextAudioReservationDetector(MediaContextProvider contextProvider, Clock clock) {
        this.contextProvider = Objects.requireNonNull(contextProvider);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public boolean isOutputReserved() {
        return reservation().blockTts();
    }

    public AudioReservation reservation() {
        AudioReservation current = evaluateReservation();
        AudioReservation previous = lastReservation.getAndSet(current);

        if (previous != null && previous != current) {
            LOGGER.info(MEDIA, "event=MEDIA_RESERVATION_CHANGED from={} to={}", previous, current);
        }
        return current;
    }

    private AudioReservation evaluateReservation() {
        MediaContextSnapshot context;
        MediaSample<ProcessPresence> process;

        try {
            context = Objects.requireNonNull(contextProvider.current());
        }
        catch (RuntimeException e) {
            return AudioReservation.UNKNOWN;
        }
        process = context.tidalProcess();
        if (!process.isFresh(clock, PROCESS_MAX_AGE)) {
            return AudioReservation.UNKNOWN;
        }
        return switch (process.value()) {
            case RUNNING -> AudioReservation.POTENTIALLY_RESERVED;
            case UNKNOWN -> AudioReservation.UNKNOWN;
            case NOT_RUNNING -> hasContradictoryPlayback(context) ? AudioReservation.UNKNOWN : AudioReservation.AVAILABLE;
        };
    }

    private boolean hasContradictoryPlayback(MediaContextSnapshot context) {
        var playback = context.tidalPlayback();

        if (!playback.isFresh(clock, PLAYBACK_MAX_AGE)) {
            return false;
        }
        return playback.value().filter(state -> state == MediaPlaybackState.PLAYING).isPresent();
    }
}

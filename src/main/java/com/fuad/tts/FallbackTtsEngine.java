package com.fuad.tts;

import java.time.Duration;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Uses {@code primary} while it works and transparently serves requests from {@code fallback} when it does not.
 *
 * <ul>
 *   <li>A failed request is answered by the fallback, so the user always gets audio.</li>
 *   <li>After {@code failureThreshold} consecutive failures the primary is bypassed for {@code retryAfter}, so a
 *       broken engine does not add latency to every utterance. Afterwards it is restarted and tried again.</li>
 *   <li>Both engines start eagerly so the fallback is ready the moment it is needed. The runtime only fails to start
 *       when neither engine can.</li>
 * </ul>
 */
public final class FallbackTtsEngine implements TtsEngine {
    public static final int DEFAULT_FAILURE_THRESHOLD = 3;
    public static final Duration DEFAULT_RETRY_AFTER = Duration.ofSeconds(60);

    private final TtsEngine primary;
    private final TtsEngine fallback;
    private final String primaryName;
    private final String fallbackName;
    private final int failureThreshold;
    private final long retryAfterNanos;
    private final LongSupplier nanoClock;
    private final Object lock = new Object();
    private boolean primaryDisabled;
    private long disabledAtNanos;
    private int consecutiveFailures;

    public FallbackTtsEngine(TtsEngine primary, String primaryName, TtsEngine fallback, String fallbackName) {
        this(primary, primaryName, fallback, fallbackName, DEFAULT_FAILURE_THRESHOLD, DEFAULT_RETRY_AFTER,
                System::nanoTime);
    }

    FallbackTtsEngine(TtsEngine primary, String primaryName, TtsEngine fallback, String fallbackName,
                      int failureThreshold, Duration retryAfter, LongSupplier nanoClock) {
        if (failureThreshold < 1) {
            throw new IllegalArgumentException("failureThreshold must be at least 1");
        }
        this.primary = Objects.requireNonNull(primary);
        this.fallback = Objects.requireNonNull(fallback);
        this.primaryName = Objects.requireNonNull(primaryName);
        this.fallbackName = Objects.requireNonNull(fallbackName);
        this.failureThreshold = failureThreshold;
        this.retryAfterNanos = retryAfter.toNanos();
        this.nanoClock = Objects.requireNonNull(nanoClock);
    }

    @Override
    public void start() throws Exception {
        Exception primaryFailure = null;
        try {
            primary.start();
        }
        catch (Exception e) {
            primaryFailure = e;
            System.err.println("TTS " + primaryName + " unavailable, using " + fallbackName + ": " + e.getMessage());
            synchronized (lock) {
                disablePrimary();
            }
        }
        try {
            fallback.start();
        }
        catch (Exception e) {
            if (primaryFailure != null) {
                IllegalStateException none = new IllegalStateException(
                        "No TTS engine could start (" + primaryName + ", " + fallbackName + ")", e);
                none.addSuppressed(primaryFailure);
                throw none;
            }
            System.err.println("TTS fallback " + fallbackName + " could not start: " + e.getMessage());
        }
    }

    @Override
    public TtsAudio synthesize(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("TTS text cannot be null or blank");
        }
        if (acquirePrimary()) {
            try {
                TtsAudio audio = primary.synthesize(text);
                synchronized (lock) {
                    consecutiveFailures = 0;
                }
                return audio;
            }
            catch (RuntimeException e) {
                recordPrimaryFailure(e);
            }
        }
        return fallback.synthesize(text);
    }

    @Override
    public void close() throws Exception {
        Exception failure = null;
        for (TtsEngine engine : new TtsEngine[]{primary, fallback}) {
            try {
                engine.close();
            }
            catch (Exception e) {
                if (failure == null) {
                    failure = e;
                }
                else {
                    failure.addSuppressed(e);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private boolean acquirePrimary() {
        synchronized (lock) {
            if (!primaryDisabled) {
                return true;
            }
            if (nanoClock.getAsLong() - disabledAtNanos < retryAfterNanos) {
                return false;
            }
            try {
                primary.start();
                primaryDisabled = false;
                consecutiveFailures = 0;
                System.out.println("TTS " + primaryName + " recovered");
                return true;
            }
            catch (Exception e) {
                System.err.println("TTS " + primaryName + " still unavailable: " + e.getMessage());
                disablePrimary();
                return false;
            }
        }
    }

    private void recordPrimaryFailure(RuntimeException error) {
        synchronized (lock) {
            consecutiveFailures++;
            System.err.println("TTS " + primaryName + " failed (" + consecutiveFailures + "/" + failureThreshold
                    + "), using " + fallbackName + " for this request: " + error.getMessage());
            if (consecutiveFailures >= failureThreshold) {
                disablePrimary();
                System.err.println("TTS " + primaryName + " bypassed for " + Duration.ofNanos(retryAfterNanos)
                        .toSeconds() + "s");
            }
        }
    }

    private void disablePrimary() {
        primaryDisabled = true;
        disabledAtNanos = nanoClock.getAsLong();
    }
}

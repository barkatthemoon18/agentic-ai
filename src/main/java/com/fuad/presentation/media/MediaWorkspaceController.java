package com.fuad.presentation.media;

import com.fuad.audio.output.AudioOutputProvider;
import com.fuad.audio.output.AudioOutputSnapshot;
import com.fuad.media.MediaPlaybackState;
import com.fuad.media.MediaSessionProvider;
import com.fuad.media.MediaSessionSnapshot;
import com.fuad.media.control.*;
import com.fuad.media.enrichment.MediaEnrichmentProvider;
import com.fuad.media.enrichment.MediaEnrichmentSnapshot;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class MediaWorkspaceController implements MediaActionHandler, MediaActionExecutor, AutoCloseable {
    private static final long REFRESH_MILLIS = 500L;
    private final AtomicReference<MediaWorkspaceSnapshot> current = new AtomicReference<>(MediaWorkspaceSnapshot.unavailable());
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "ares-media-session");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final MediaSessionProvider provider;
    private final AudioOutputProvider audioOutputProvider;
    private final MediaEnrichmentProvider enrichmentProvider;

    public MediaWorkspaceController(MediaSessionProvider provider) {
        this(provider, AudioOutputProvider.unavailable(), MediaEnrichmentProvider.unavailable());
    }

    public MediaWorkspaceController(MediaSessionProvider provider, AudioOutputProvider audioOutputProvider) {
        this(provider, audioOutputProvider, MediaEnrichmentProvider.unavailable());
    }

    public MediaWorkspaceController(MediaSessionProvider provider, AudioOutputProvider audioOutputProvider,
                                    MediaEnrichmentProvider enrichmentProvider) {
        this.provider = Objects.requireNonNull(provider);
        this.audioOutputProvider = Objects.requireNonNull(audioOutputProvider);
        this.enrichmentProvider = Objects.requireNonNull(enrichmentProvider);
    }

    public void start() {
        if (closed.get()) {
            throw new IllegalStateException("Media controller is closed");
        }
        if (!started.compareAndSet(false, true)) {
            return;
        }
        executor.scheduleWithFixedDelay(this::refreshSafely, 0, REFRESH_MILLIS, TimeUnit.MILLISECONDS);
    }

    public MediaWorkspaceSnapshot current() {
        return current.get();
    }

    @Override
    public boolean submit(MediaAction action) {
        return enqueue(action, new CompletableFuture<>());
    }

    @Override
    public CompletionStage<MediaActionResult> executeAsync(MediaAction action) {
        var result = new CompletableFuture<MediaActionResult>();

        enqueue(action, result);
        return result;
    }

    @Override
    public void close() throws Exception {
        executor.shutdownNow();
        clearProvider(provider, "media provider");
        clearProvider(audioOutputProvider, "audio output provider");
        clearProvider(enrichmentProvider, "media enrichment provider");
    }

    private boolean enqueue(MediaAction action, CompletableFuture<MediaActionResult> result) {
        Objects.requireNonNull(action);
        if (closed.get()) {
            result.complete(new MediaActionResult(action, MediaActionStatus.CLOSED));
            return false;
        }
        try {
            executor.execute(() -> {
                MediaActionResult outcome;

                try {
                    outcome = performAction(action);
                }
                finally {
                    refreshSafely();
                }
                result.complete(outcome);
            });
            return true;
        }
        catch (RejectedExecutionException e) {
            result.complete(new MediaActionResult(action, MediaActionStatus.CLOSED));
            return false;
        }
    }

    private MediaActionResult performAction(MediaAction action) {
        try {
            boolean accepted = switch (action) {
                case PREVIOUS -> provider.previous();
                case NEXT -> provider.next();
                case PAUSE -> provider.pause();
                case RESUME -> provider.resume();
                case PLAY_PAUSE -> provider.playPause();
            };
            return new MediaActionResult(action, accepted ? MediaActionStatus.ACKNOWLEDGED : MediaActionStatus.REJECTED);
        }
        catch (Exception e) {
            System.err.printf("Media action error [%s]: %s%n", action, e.getMessage());
            return new MediaActionResult(action, MediaActionStatus.FAILED);
        }
    }

    private void refreshSafely() {
        try {
            MediaSessionSnapshot session = currentMediaSession();
            AudioOutputSnapshot audio = currentAudioOutput();
            MediaEnrichmentSnapshot enrichment = currentEnrichment();
            Optional<MediaPlaybackState> mediaPlaybackState = currentMediaPlaybackState();
            current.set(toWorkspaceSnapshot(session, audio, enrichment, mediaPlaybackState));
        }
        catch (Exception e) {
            current.set(MediaWorkspaceSnapshot.unavailable());
            System.err.println("Unable to refresh media session: " + e.getMessage());
        }
    }

    private MediaSessionSnapshot currentMediaSession() {
        try {
            return provider.current();
        }
        catch (RuntimeException e) {
            System.err.println("Unable to refresh media session: " + e.getMessage());
            return MediaSessionSnapshot.unavailable();
        }
    }

    private AudioOutputSnapshot currentAudioOutput() {
        try {
            return audioOutputProvider.current();
        }
        catch (RuntimeException e) {
            System.err.println("Unable to refresh audio output: " + e.getMessage());
        }
        return AudioOutputSnapshot.unavailable();
    }

    private MediaEnrichmentSnapshot currentEnrichment() {
        try {
            return enrichmentProvider.current();
        }
        catch (RuntimeException e) {
            System.err.println("Unable to refresh media enrichment: " + e.getMessage());
            return MediaEnrichmentSnapshot.unavailable();
        }
    }

    private MediaWorkspaceSnapshot toWorkspaceSnapshot(MediaSessionSnapshot session, AudioOutputSnapshot audio,
                                                       MediaEnrichmentSnapshot enrichment, Optional<MediaPlaybackState> mediaPlaybackState) {
        return new MediaWorkspaceSnapshot(session.available(), session.sourceApplication(), session.currentTrack(),
                session.positionSeconds(), session.playbackState(), audio.volume(), audio.deviceName(), enrichment.quality(),
                enrichment.queue(), mediaPlaybackState);
    }

    private Optional<MediaPlaybackState> currentMediaPlaybackState() {
        try {
            Optional<MediaPlaybackState> state = provider.playbackState("TIDAL");
            // System.out.println("MEDIA -> TIDAL playback state: " + state.map(Enum::name).orElse("UNAVAILABLE"));
            return state;
        }
        catch (RuntimeException e) {
            System.err.println("Unable to resolve TIDAL playback state: " + e.getMessage());
            return Optional.empty();
        }
    }

    private static void clearProvider(AutoCloseable provider, String label) {
        try {
            provider.close();
        }
        catch (Exception e) {
            System.err.println("Unable to close " + label + ": " + e.getMessage());
        }
    }
}

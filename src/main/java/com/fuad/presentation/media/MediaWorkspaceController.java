package com.fuad.presentation.media;

import com.fuad.audio.output.AudioOutputProvider;
import com.fuad.audio.output.AudioOutputSnapshot;
import com.fuad.media.MediaSessionProvider;
import com.fuad.media.MediaSessionSnapshot;
import com.fuad.media.enrichment.MediaEnrichmentProvider;
import com.fuad.media.enrichment.MediaEnrichmentSnapshot;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class MediaWorkspaceController implements MediaActionHandler, AutoCloseable {
    private static final long REFRESH_MILLIS = 500L;
    private final AtomicReference<MediaWorkspaceSnapshot> current = new AtomicReference<>(MediaWorkspaceSnapshot.unavailable());
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "ares-media-session");
        thread.setDaemon(true);
        return thread;
    });
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
        executor.scheduleWithFixedDelay(this::refreshSafely, 0, REFRESH_MILLIS, TimeUnit.MILLISECONDS);
    }

    public MediaWorkspaceSnapshot current() {
        return current.get();
    }

    @Override
    public boolean submit(MediaAction action) {
        if (executor.isShutdown()) {
            return false;
        }
        executor.execute(() -> execute(action));
        return true;
    }

    @Override
    public void close() throws Exception {
        executor.shutdownNow();
        clearProvider(provider, "media provider");
        clearProvider(audioOutputProvider, "audio output provider");
        clearProvider(enrichmentProvider, "media enrichment provider");
    }

    private void execute(MediaAction action) {
        switch (action) {
            case PREVIOUS -> provider.previous();
            case PLAY_PAUSE -> provider.playPause();
            case NEXT -> provider.next();
        }
        refreshSafely();
    }

    private void refreshSafely() {
        try {
            MediaSessionSnapshot session = currentMediaSession();
            AudioOutputSnapshot audio = currentAudioOutput();
            MediaEnrichmentSnapshot enrichment = currentEnrichment();
            current.set(toWorkspaceSnapshot(session, audio, enrichment));
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
                                                       MediaEnrichmentSnapshot enrichment) {
        return new MediaWorkspaceSnapshot(session.available(), session.sourceApplication(), session.currentTrack(),
                session.positionSeconds(), session.playbackState(), audio.volume(), audio.deviceName(), enrichment.quality(),
                enrichment.queue());
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

package com.fuad.bootstrap;

import com.fuad.audio.output.AudioOutputProvider;
import com.fuad.audio.output.windows.WindowsAudioOutputProvider;
import com.fuad.media.MediaSessionProvider;
import com.fuad.media.context.MediaContextService;
import com.fuad.media.enrichment.MediaEnrichmentProvider;
import com.fuad.media.enrichment.tidal.TidalMediaEnrichmentProvider;
import com.fuad.media.windows.WindowsMediaSessionProvider;
import com.fuad.presentation.media.MediaWorkspaceController;

import java.util.Objects;
import java.util.function.Supplier;

final class MediaBootstrap {
    private final Supplier<MediaSessionProvider> sessions;
    private final Supplier<AudioOutputProvider> output;
    private final Supplier<MediaEnrichmentProvider> enrichment;

    MediaBootstrap() {
        this(WindowsMediaSessionProvider::new, WindowsAudioOutputProvider::new, TidalMediaEnrichmentProvider::new);
    }

    MediaBootstrap(Supplier<MediaSessionProvider> sessions, Supplier<AudioOutputProvider> output,
                   Supplier<MediaEnrichmentProvider> enrichment) {
        this.sessions = Objects.requireNonNull(sessions);
        this.output = Objects.requireNonNull(output);
        this.enrichment = Objects.requireNonNull(enrichment);
    }

    MediaWorkspaceController create(ResourceCleanup cleanup) {
        Objects.requireNonNull(cleanup, "cleanup");
        MediaSessionProvider mediaSessions = createSafely("media session", sessions, MediaSessionProvider::unavailable);
        AudioOutputProvider audioOutput = createSafely("audio output", output, AudioOutputProvider::unavailable);
        MediaEnrichmentProvider mediaEnrichment = createSafely("media enrichment", enrichment,
                MediaEnrichmentProvider::unavailable);

        MediaContextService contextService = new MediaContextService(mediaSessions, audioOutput, mediaEnrichment);
        MediaWorkspaceController controller = new MediaWorkspaceController(contextService);
        cleanup.register(ResourceCleanup.Resource.MEDIA, controller);
        controller.start();
        return controller;
    }

    private static <T> T createSafely(String name, Supplier<? extends T> factory, Supplier<? extends T> fallback) {
        try {
            T provider = factory.get();
            if (provider == null) {
                throw new IllegalStateException("factory returned null");
            }
            System.out.println("MEDIA -> " + name + " provider ready");
            return provider;
        }
        catch (RuntimeException | LinkageError e) {
            System.err.println("MEDIA -> " + name + " provider unavailable: " + e);
            return fallback.get();
        }
    }
}

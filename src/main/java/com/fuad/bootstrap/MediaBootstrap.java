package com.fuad.bootstrap;

import com.fuad.audio.output.AudioOutputProvider;
import com.fuad.audio.output.windows.WindowsAudioOutputProvider;
import com.fuad.media.MediaSessionProvider;
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
        AudioOutputProvider audioOutput = output.get();
        MediaEnrichmentProvider mediaEnrichment = enrichment.get();
        MediaSessionProvider mediaSessions;
        try {
            mediaSessions = sessions.get();
            System.out.println("MEDIA -> Windowws media provider ready");
        }
        catch (RuntimeException | LinkageError e) {
            System.err.println("MEDIA -> Windows media provider unavailable: " + e.getMessage());
            mediaSessions = MediaSessionProvider.unavailable();
        }
        MediaWorkspaceController controller = new MediaWorkspaceController(mediaSessions, audioOutput, mediaEnrichment);
        cleanup.register(ResourceCleanup.Resource.MEDIA, controller);
        controller.start();
        return controller;
    }
}

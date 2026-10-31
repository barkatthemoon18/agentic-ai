package com.fuad.presentation.media;

import com.fuad.audio.output.AudioOutputProvider;
import com.fuad.media.MediaSessionProvider;
import com.fuad.media.context.MediaContextProvider;
import com.fuad.media.context.MediaContextService;
import com.fuad.media.control.MediaAction;
import com.fuad.media.control.MediaActionExecutor;
import com.fuad.media.control.MediaActionHandler;
import com.fuad.media.control.MediaActionResult;
import com.fuad.media.enrichment.MediaEnrichmentProvider;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

public class MediaWorkspaceController implements MediaActionHandler, MediaActionExecutor, AutoCloseable {
    private final MediaContextService service;
    private final MediaWorkspaceMapper mapper;

    public MediaWorkspaceController(MediaContextService service) {
        this(service, new MediaWorkspaceMapper());
    }

    public MediaWorkspaceController(MediaContextService service, MediaWorkspaceMapper mapper) {
        this.service = Objects.requireNonNull(service);
        this.mapper = Objects.requireNonNull(mapper);
    }

    public MediaWorkspaceController(MediaSessionProvider sessions) {
        this(sessions, AudioOutputProvider.unavailable(), MediaEnrichmentProvider.unavailable());
    }

    public MediaWorkspaceController(MediaSessionProvider sessions, AudioOutputProvider audio) {
        this(sessions, audio, MediaEnrichmentProvider.unavailable());
    }

    public MediaWorkspaceController(MediaSessionProvider sessions, AudioOutputProvider audio, MediaEnrichmentProvider enrichment) {
        this(new MediaContextService(sessions, audio, enrichment));
    }

    public void start() {
        service.start();
    }

    public MediaWorkspaceSnapshot current() {
        return mapper.map(service.current());
    }

    public MediaContextProvider contextProvider() {
        return service;
    }

    @Override
    public CompletionStage<MediaActionResult> executeAsync(MediaAction action) {
        return service.executeAsync(action);
    }

    @Override
    public boolean submit(MediaAction action) {
        return service.submit(action);
    }

    @Override
    public void close() throws Exception {
        service.close();
    }
}

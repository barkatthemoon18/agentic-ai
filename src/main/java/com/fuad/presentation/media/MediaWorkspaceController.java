package com.fuad.presentation.media;

import com.fuad.media.MediaSessionProvider;
import com.fuad.media.MediaSessionSnapshot;

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

    public MediaWorkspaceController(MediaSessionProvider provider) {
        this.provider = Objects.requireNonNull(provider);
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
        try {
            provider.close();
        }
        catch (Exception e) {
            System.err.println("Unable to close media provider: " + e.getMessage());
        }
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
            MediaSessionSnapshot session = provider.current();
            current.set(toWorkspaceSnapshot(session));
        }
        catch (Exception e) {
            current.set(MediaWorkspaceSnapshot.unavailable());
            System.err.println("Unable to refresh media session: " + e.getMessage());
        }
    }

    private MediaWorkspaceSnapshot toWorkspaceSnapshot(MediaSessionSnapshot session) {
        return new MediaWorkspaceSnapshot(session.available(), session.sourceApplication(), session.currentTrack(),
                session.positionSeconds(), session.playbackState(), 0.0, "", "");
    }
}

package com.fuad.bootstrap;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Owns shutdown actions in dependency order, including partially initialized applications. */
final class ResourceCleanup implements AutoCloseable {
    enum Resource {
        MODEL_RUNTIME, MEDIA, CORE_VISUAL, CORE_VISUAL_SOURCE, CAPTURE, INTERACTION, SPEECH_PROCESSOR, TTS,
        VISUAL_OUTPUT, STT, VAD, JAVAFX_RUNTIME
    }

    private final Map<Resource, AutoCloseable> actions = new EnumMap<>(Resource.class);
    private boolean closed;

    // Replacing a slot transfers ownership, e.g. visual output to its coordinator.
    synchronized void register(Resource resource, AutoCloseable action) {
        Objects.requireNonNull(resource);
        Objects.requireNonNull(action);
        if (closed) {
            closeResource(resource, action);
            throw new IllegalStateException("Application resources are already closed");
        }
        actions.put(resource, action);
    }

    /** Transfers a constructed bundle atomically, including ownership on rejection. */
    synchronized void registerAll(Map<Resource, AutoCloseable> resources) {
        Map<Resource, AutoCloseable> validated = Map.copyOf(resources);
        if (closed) {
            try (ResourceCleanup rejected = new ResourceCleanup()) {
                rejected.actions.putAll(validated);
            }
            throw new IllegalStateException("Application resources are already closed");
        }
        actions.putAll(validated);
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        for (Resource resource : Resource.values()) {
            AutoCloseable action = actions.remove(resource);
            if (action == null) {
                continue;
            }
            closeResource(resource, action);
        }
    }

    private void closeResource(Resource resource, AutoCloseable action) {
        try {
            action.close();
        }
        catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            System.err.println("Unable to close " + resource + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
}

package com.fuad;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Owns shutdown actions in dependency order, including partially initialized applications. */
final class ResourceCleanup implements AutoCloseable {
    enum Resource {
        MODEL_RUNTIME, CAPTURE, INTERACTION, SPEECH_PROCESSOR, TTS,
        VISUAL_OUTPUT, STT, VAD, JAVAFX_RUNTIME
    }

    private final Map<Resource, AutoCloseable> actions = new EnumMap<>(Resource.class);

    // Replacing a slot transfers ownership, e.g. visual output to its coordinator.
    void register(Resource resource, AutoCloseable action) {
        actions.put(Objects.requireNonNull(resource), Objects.requireNonNull(action));
    }

    @Override
    public void close() {
        for (Resource resource : Resource.values()) {
            AutoCloseable action = actions.remove(resource);
            if (action == null) {
                continue;
            }
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
}

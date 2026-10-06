package com.fuad.assistant.local;

@FunctionalInterface
public interface LocalQwenReadiness {
    void ensureReady();

    static LocalQwenReadiness alwaysReady() {
        return () -> {
            /* No-op intentionally */
        };
    }
}

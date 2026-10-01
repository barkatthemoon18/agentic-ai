package com.fuad.presentation.interaction;

public record InteractionDisplayConfig(String displayId, Fallback fallback,
                                       boolean allowPrimary) {
    public InteractionDisplayConfig {
        displayId = displayId == null ? "" : displayId.trim();
        fallback = fallback == null ? new Fallback(1920, 1080) : fallback;
    }

    public record Fallback(int width, int height) {
        public Fallback {
            if (width < 1 || height < 1) {
                throw new IllegalArgumentException("fallback dimensions must be positive");
            }
        }
    }

    public static InteractionDisplayConfig defaults() {
        return new InteractionDisplayConfig("", new Fallback(1920, 1080), false);
    }
}

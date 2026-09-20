package com.fuad.view.workspace.system;

public record SystemAudioSnapshot(
        String outputDevice,
        String inputDevice,
        double volume,
        boolean muted) {
    /* Empty intentionally */
}

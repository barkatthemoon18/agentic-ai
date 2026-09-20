package com.fuad.view.workspace.system;

public record SystemDisplaySnapshot(
        String id,
        int width,
        int height,
        double refreshRate,
        boolean primary,
        boolean interactionDisplay) {
    /* Empty intentionally */
}

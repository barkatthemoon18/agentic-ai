package com.fuad.view.workspace.system;

public record SystemNetworkSnapshot(
        String status,
        String adapter,
        String ipAddress,
        double linkSpeedMbps,
        double downloadMbps,
        double uploadMbps,
        double latencyMs) {
    /* Empty intentionally */
}

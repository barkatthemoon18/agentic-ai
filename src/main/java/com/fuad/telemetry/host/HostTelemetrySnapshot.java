package com.fuad.telemetry.host;

public record HostTelemetrySnapshot(
        double cpuUsage,
        double ramUsedGb,
        double ramTotalGb,
        String localIp,
        double downloadMbps,
        double uploadMbps) {
    /* Empty intentionally */
}

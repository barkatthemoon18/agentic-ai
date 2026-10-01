package com.fuad.telemetry.gpu;

public record GpuTelemetrySnapshot(
        boolean available,
        double usage,
        double vramUsedGb,
        double vramTotalGb,
        double temperature) {

    public static GpuTelemetrySnapshot unavailable() {
        return new GpuTelemetrySnapshot(false, 0.0, 0.0, 0.0, 0.0);
    }
}

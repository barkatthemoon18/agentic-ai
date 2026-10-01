package com.fuad.telemetry.gpu;

public interface GpuTelemetryProvider extends AutoCloseable {

    GpuTelemetrySnapshot sample();

    @Override
    default void close() {
        /* Nothing */
    }
}

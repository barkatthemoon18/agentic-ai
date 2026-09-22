package com.fuad.telemetry.host;

public interface HostTelemetryProvider extends AutoCloseable {
    HostTelemetrySnapshot sample();

    @Override
    default void close() throws Exception {
        /* Nothing by default */
    }
}

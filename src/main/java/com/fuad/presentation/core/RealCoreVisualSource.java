package com.fuad.presentation.core;

import com.fuad.telemetry.gpu.GpuTelemetryProvider;
import com.fuad.telemetry.gpu.GpuTelemetrySnapshot;
import com.fuad.telemetry.host.HostTelemetryProvider;
import com.fuad.telemetry.host.HostTelemetrySnapshot;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class RealCoreVisualSource implements CoreVisualSource {
    private final HostTelemetryProvider telemetryProvider;
    private final GpuTelemetryProvider gpuTelemetryProvider;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform()
            .name("ares-core-telemetry")
            .daemon()
            .factory());

    public RealCoreVisualSource(HostTelemetryProvider telemetryProvider, GpuTelemetryProvider gpuTelemetryProvider) {
        this.telemetryProvider = telemetryProvider;
        this.gpuTelemetryProvider = gpuTelemetryProvider;
    }

    @Override
    public void start(Consumer<CoreVisualSnapshot> consumer) {
        executor.scheduleAtFixedRate(() -> poll(consumer), 0, 1, TimeUnit.SECONDS);
    }

    @Override
    public void close() throws Exception {
        executor.shutdownNow();
        telemetryProvider.close();
        gpuTelemetryProvider.close();
    }

    private void poll(Consumer<CoreVisualSnapshot> consumer) {
        try {
            GpuTelemetrySnapshot gpuSnapshot;
            HostTelemetrySnapshot snapshot = telemetryProvider.sample();
            try {
                gpuSnapshot = gpuTelemetryProvider.sample();
            }
            catch (RuntimeException | LinkageError e) {
                System.err.println("GPU telemetry failed: " + e.getMessage());
                gpuSnapshot = GpuTelemetrySnapshot.unavailable();
            }

            consumer.accept(new CoreVisualSnapshot(AssistantVisualState.IDLE, new CoreVisualSnapshot.SystemSnapshot(
                    snapshot.cpuUsage(),
                    snapshot.ramUsedGb(),
                    snapshot.ramTotalGb()), new CoreVisualSnapshot.GpuSnapshot(gpuSnapshot.usage(), gpuSnapshot.vramUsedGb(),
                    gpuSnapshot.vramTotalGb(), gpuSnapshot.temperature()),
                    new CoreVisualSnapshot.NetworkSnapshot(snapshot.localIp(), snapshot.downloadMbps(), snapshot.uploadMbps()),
                    new CoreVisualSnapshot.RuntimeSnapshot("READY", "READY", "READY", "READY")));
        }
        catch (Exception | LinkageError e) {
            System.err.println("Telemetry sampling failed: " + e.getMessage());
        }
    }
}

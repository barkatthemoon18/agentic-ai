package com.fuad.presentation.core;

import com.fuad.telemetry.gpu.GpuTelemetryProvider;
import com.fuad.telemetry.gpu.GpuTelemetrySnapshot;
import com.fuad.telemetry.host.HostTelemetryProvider;
import com.fuad.telemetry.host.HostTelemetrySnapshot;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class RealCoreVisualSource implements CoreVisualSource {
    private final AssistantVisualStateStore assistantStateStore;
    private final Object publishLock = new Object();
    private final HostTelemetryProvider telemetryProvider;
    private final GpuTelemetryProvider gpuTelemetryProvider;
    private final RuntimeStatusCoordinator runtimeStatusCoordinator;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform()
            .name("ares-core-telemetry")
            .daemon()
            .factory());
    private volatile Consumer<CoreVisualSnapshot> consumer;
    private CoreVisualSnapshot latestSnapshot;
    private AutoCloseable stateSubscription;

    public RealCoreVisualSource(HostTelemetryProvider telemetryProvider, GpuTelemetryProvider gpuTelemetryProvider,
                                RuntimeStatusCoordinator runtimeStatusCoordinator, AssistantVisualStateStore assistantStateStore) {
        this.telemetryProvider = telemetryProvider;
        this.gpuTelemetryProvider = gpuTelemetryProvider;
        this.runtimeStatusCoordinator = runtimeStatusCoordinator;
        this.assistantStateStore = assistantStateStore;
    }

    @Override
    public void start(Consumer<CoreVisualSnapshot> consumer) {
        this.consumer = Objects.requireNonNull(consumer);
        stateSubscription = assistantStateStore.subscribe(this::publishAssistantState);
        executor.scheduleAtFixedRate(this::poll, 0, 1, TimeUnit.SECONDS);
    }

    @Override
    public void close() throws Exception {
        if (stateSubscription != null) {
            stateSubscription.close();
        }
        executor.shutdownNow();
        telemetryProvider.close();
        gpuTelemetryProvider.close();
    }

    private void poll() {
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

            synchronized (publishLock) {
                CoreVisualSnapshot next = new CoreVisualSnapshot(assistantStateStore.current(),
                        new CoreVisualSnapshot.SystemSnapshot(snapshot.cpuUsage(), snapshot.ramUsedGb(), snapshot.ramTotalGb()),
                        new CoreVisualSnapshot.GpuSnapshot(gpuSnapshot.usage(), gpuSnapshot.vramUsedGb(), gpuSnapshot.vramTotalGb(),
                                gpuSnapshot.temperature()),
                        new CoreVisualSnapshot.NetworkSnapshot(snapshot.localIp(), snapshot.downloadMbps(),
                                snapshot.uploadMbps()), runtimeStatusCoordinator.snapshot());
                latestSnapshot = next;
                consumer.accept(next);
            }
        }
        catch (Exception | LinkageError e) {
            System.err.println("Telemetry sampling failed: " + e.getMessage());
        }
    }

    private void publishAssistantState(AssistantVisualState state) {
        synchronized (publishLock) {
            if (consumer == null || latestSnapshot == null) {
                return;
            }
            CoreVisualSnapshot updated = latestSnapshot.withAssistantVisualState(state);
            latestSnapshot = updated;
            consumer.accept(updated);
        }
    }
}

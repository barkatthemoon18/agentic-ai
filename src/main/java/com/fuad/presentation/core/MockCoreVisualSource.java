package com.fuad.presentation.core;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class MockCoreVisualSource implements AutoCloseable {
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform()
            .daemon()
            .name("ares-core-mock")
            .factory());
    private long tick;

    public void start(Consumer<CoreVisualSnapshot> consumer) {
        executor.scheduleAtFixedRate(() -> consumer.accept(nextSnapshot()), 0, 1, TimeUnit.SECONDS);
    }

    @Override
    public void close() throws Exception {
        executor.shutdownNow();
    }

    private CoreVisualSnapshot nextSnapshot() {
        double phase = tick++ / 5.0;

        return new CoreVisualSnapshot(AssistantVisualState.IDLE, new CoreVisualSnapshot.SystemSnapshot(32.0 + Math.sin(phase) * 12.0, 14.8, 32.0),
                new CoreVisualSnapshot.GpuSnapshot(46.0 + Math.sin(phase * 0.7) * 18.0, 7.2, 12.0, 52.0),
                new CoreVisualSnapshot.NetworkSnapshot("192.168.1.42", 14.5, 3.2),
                new CoreVisualSnapshot.RuntimeSnapshot(RuntimeVisualState.READY, RuntimeVisualState.READY, RuntimeVisualState.READY, RuntimeVisualState.READY));
    }
}
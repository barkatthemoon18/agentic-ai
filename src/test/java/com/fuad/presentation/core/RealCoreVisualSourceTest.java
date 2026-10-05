package com.fuad.presentation.core;

import com.fuad.pipeline.AssistantActivityState;
import com.fuad.telemetry.gpu.GpuTelemetryProvider;
import com.fuad.telemetry.gpu.GpuTelemetrySnapshot;
import com.fuad.telemetry.host.HostTelemetryProvider;
import com.fuad.telemetry.host.HostTelemetrySnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RealCoreVisualSourceTest {
    private final HostTelemetryProvider host = mock(HostTelemetryProvider.class);
    private final GpuTelemetryProvider gpu = mock(GpuTelemetryProvider.class);
    private final AssistantVisualStateStore state = new AssistantVisualStateStore();
    private final RuntimeStatusCoordinator runtime = new RuntimeStatusCoordinator();
    private final LinkedBlockingQueue<CoreVisualSnapshot> published = new LinkedBlockingQueue<>();

    private RealCoreVisualSource source() {
        when(host.sample()).thenReturn(new HostTelemetrySnapshot(25, 8, 32, "192.168.1.5", 10, 2));
        when(gpu.sample()).thenReturn(new GpuTelemetrySnapshot(true, 40, 3, 12, 55));
        return new RealCoreVisualSource(host, gpu, runtime, state);
    }

    @Test
    void telemetryShouldPublishRealValuesAndRepublishStateWithoutResampling() throws Exception {
        try (RealCoreVisualSource source = source()) {
            runtime.setStt(RuntimeVisualState.READY);
            state.setDegraded(true);
            source.start(published::add);
            CoreVisualSnapshot initial = take();
            assertEquals(AssistantVisualState.DEGRADED, initial.assistantVisualState());
            assertEquals(new CoreVisualSnapshot.SystemSnapshot(25, 8, 32), initial.systemSnapshot());
            assertEquals(new CoreVisualSnapshot.GpuSnapshot(40, 3, 12, 55), initial.gpuSnapshot());
            assertEquals(new CoreVisualSnapshot.NetworkSnapshot("192.168.1.5", 10, 2), initial.networkSnapshot());
            assertEquals(RuntimeVisualState.READY, initial.runtimeSnapshot().sttState());

            state.onStateChanged(AssistantActivityState.SPEAKING);
            CoreVisualSnapshot changed = take();
            assertEquals(AssistantVisualState.SPEAKING, changed.assistantVisualState());
            assertSame(initial.systemSnapshot(), changed.systemSnapshot());
            assertSame(initial.gpuSnapshot(), changed.gpuSnapshot());
            assertSame(initial.networkSnapshot(), changed.networkSnapshot());
            assertSame(initial.runtimeSnapshot(), changed.runtimeSnapshot());
        }
        verify(host).close();
        verify(gpu).close();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void unavailableGpuShouldNotPreventHostTelemetry(boolean linkageFailure) throws Exception {
        try (RealCoreVisualSource source = source()) {
            when(gpu.sample()).thenAnswer(invocation -> {
                if (linkageFailure) throw new UnsatisfiedLinkError("NVML missing");
                throw new IllegalStateException("GPU unavailable");
            });
            source.start(published::add);
            CoreVisualSnapshot snapshot = take();
            assertEquals(new CoreVisualSnapshot.GpuSnapshot(0, 0, 0, 0), snapshot.gpuSnapshot());
            assertEquals(25, snapshot.systemSnapshot().cpuUsage());
            assertEquals("192.168.1.5", snapshot.networkSnapshot().localIp());
        }
    }

    @Test
    void hostSamplingFailureShouldAllowLaterPollsToRecover() throws Exception {
        try (RealCoreVisualSource source = source()) {
            when(host.sample()).thenThrow(new IllegalStateException("temporary host failure"))
                    .thenReturn(new HostTelemetrySnapshot(50, 16, 32, "192.168.1.6", 20, 4));
            source.start(published::add);
            CoreVisualSnapshot recovered = take();
            assertEquals(50, recovered.systemSnapshot().cpuUsage());
            assertEquals("192.168.1.6", recovered.networkSnapshot().localIp());
            verify(host, atLeast(2)).sample();
        }
    }

    @Test
    void stateChangesBeforeFirstTelemetryMustWaitForRealData() throws Exception {
        java.util.concurrent.CountDownLatch entered = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        try (RealCoreVisualSource source = source()) {
            when(host.sample()).thenAnswer(invocation -> {
                entered.countDown();
                if (!release.await(3, TimeUnit.SECONDS)) throw new IllegalStateException("test timeout");
                return new HostTelemetrySnapshot(25, 8, 32, "192.168.1.5", 10, 2);
            });
            source.start(published::add);
            try {
                assertTrue(entered.await(3, TimeUnit.SECONDS));
                state.onStateChanged(AssistantActivityState.LISTENING);
                assertTrue(published.isEmpty());
            }
            finally {
                release.countDown();
            }
            assertEquals(AssistantVisualState.LISTENING, take().assistantVisualState());
        }
    }

    private CoreVisualSnapshot take() throws InterruptedException {
        CoreVisualSnapshot snapshot = published.poll(3, TimeUnit.SECONDS);
        assertNotNull(snapshot, "Telemetry must be published within three seconds");
        return snapshot;
    }
}

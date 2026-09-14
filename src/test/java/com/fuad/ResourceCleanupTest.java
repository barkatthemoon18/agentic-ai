package com.fuad;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.fuad.ResourceCleanup.Resource.*;
import static org.junit.jupiter.api.Assertions.*;

class ResourceCleanupTest {
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 3})
    void shouldAttemptAllClosuresInDependencyOrderDespiteFailures(int failures) {
        List<ResourceCleanup.Resource> closed = new ArrayList<>();
        ResourceCleanup cleanup = new ResourceCleanup();
        ResourceCleanup.Resource[] resources = ResourceCleanup.Resource.values();
        for (int i = resources.length - 1; i >= 0; i--) {
            ResourceCleanup.Resource resource = resources[i];
            cleanup.register(resource, () -> {
                closed.add(resource);
                if (resource.ordinal() < failures) {
                    throw new IllegalStateException("simulated " + resource);
                }
            });
        }
        assertDoesNotThrow(cleanup::close);
        assertEquals(List.of(resources), closed);
        cleanup.close();
        assertEquals(resources.length, closed.size());
    }

    @Test
    void shouldCloseRegisteredResourcesOnPartialStartupFailure() {
        List<ResourceCleanup.Resource> closed = new ArrayList<>();
        assertThrows(IllegalStateException.class, () -> {
            try (ResourceCleanup cleanup = new ResourceCleanup()) {
                cleanup.register(STT, () -> closed.add(STT));
                cleanup.register(VISUAL_OUTPUT, () -> closed.add(VISUAL_OUTPUT));
                throw new IllegalStateException("startup failed before coordinator creation");
            }
        });
        assertEquals(List.of(VISUAL_OUTPUT, STT), closed);
    }

    @Test
    void shouldTransferVisualOwnershipWithoutClosingTwice() {
        AtomicInteger visualCloses = new AtomicInteger();
        AtomicInteger coordinatorCloses = new AtomicInteger();
        try (ResourceCleanup cleanup = new ResourceCleanup()) {
            cleanup.register(VISUAL_OUTPUT, visualCloses::incrementAndGet);
            cleanup.register(VISUAL_OUTPUT, () -> {
                coordinatorCloses.incrementAndGet();
                visualCloses.incrementAndGet();
            });
        }
        assertEquals(1, coordinatorCloses.get());
        assertEquals(1, visualCloses.get());
    }

    @Test
    void shouldPreserveInterruptionAndAttemptRemainingClosures() {
        AtomicInteger closed = new AtomicInteger();
        try {
            try (ResourceCleanup cleanup = new ResourceCleanup()) {
                cleanup.register(TTS, () -> { throw new InterruptedException("interrupted close"); });
                cleanup.register(VISUAL_OUTPUT, closed::incrementAndGet);
            }
            assertTrue(Thread.currentThread().isInterrupted());
            assertEquals(1, closed.get());
        }
        finally {
            Thread.interrupted();
        }
    }
}

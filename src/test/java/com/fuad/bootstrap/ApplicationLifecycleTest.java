package com.fuad.bootstrap;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.fuad.bootstrap.ResourceCleanup.Resource.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApplicationLifecycleTest {
    @Test
    void waitShouldEndOnlyAfterHookHasFinishedSharedCleanup() throws Exception {
        ResourceCleanup resources = new ResourceCleanup();
        var hooks = mock(ApplicationLifecycle.ShutdownHooks.class);
        AtomicReference<Thread> hook = new AtomicReference<>();
        doAnswer(invocation -> { hook.set(invocation.getArgument(0)); return null; }).when(hooks).add(any());
        CountDownLatch closingResource = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger closed = new AtomicInteger();
        resources.register(CAPTURE, () -> {
            closingResource.countDown();
            assertTrue(release.await(3, TimeUnit.SECONDS));
            closed.incrementAndGet();
        });
        try (ApplicationLifecycle lifecycle = new ApplicationLifecycle(resources, hooks);
             var executor = Executors.newFixedThreadPool(3)) {
            lifecycle.installShutdownHook();
            lifecycle.installShutdownHook();
            var waiting = executor.submit(() -> { lifecycle.awaitShutdown(); return null; });
            var hookClose = executor.submit(hook.get()::run);
            assertTrue(closingResource.await(3, TimeUnit.SECONDS));
            var normalClose = executor.submit(lifecycle::close);
            try {
                assertThrows(TimeoutException.class, () -> waiting.get(50, TimeUnit.MILLISECONDS));
                assertThrows(TimeoutException.class, () -> normalClose.get(50, TimeUnit.MILLISECONDS));
            }
            finally {
                release.countDown();
            }
            hookClose.get(3, TimeUnit.SECONDS);
            normalClose.get(3, TimeUnit.SECONDS);
            waiting.get(3, TimeUnit.SECONDS);
            assertEquals(1, closed.get());
            verify(hooks).add(hook.get());
            verify(hooks).remove(hook.get());
        }
    }

    @Test
    void normalCloseShouldRemoveHookAndPermitImmediateWaitAfterwards() throws Exception {
        var hooks = mock(ApplicationLifecycle.ShutdownHooks.class);
        ApplicationLifecycle lifecycle = new ApplicationLifecycle(new ResourceCleanup(), hooks);
        lifecycle.installShutdownHook();
        lifecycle.close();
        lifecycle.close();
        lifecycle.awaitShutdown();
        verify(hooks).add(any());
        verify(hooks).remove(any());
        assertThrows(IllegalStateException.class, lifecycle::installShutdownHook);
    }

    @Test
    void interruptedWaitShouldPropagateAndFinallyCloseResources() {
        AtomicInteger closed = new AtomicInteger();
        ResourceCleanup resources = new ResourceCleanup();
        resources.register(STT, closed::incrementAndGet);
        try {
            Thread.currentThread().interrupt();
            assertThrows(InterruptedException.class, () -> {
                try (var lifecycle = new ApplicationLifecycle(resources, mock(ApplicationLifecycle.ShutdownHooks.class))) {
                    lifecycle.awaitShutdown();
                }
            });
            assertEquals(1, closed.get());
        }
        finally {
            Thread.interrupted();
        }
    }

    @Test
    void shutdownAlreadyInProgressShouldNotMakeNormalCloseFail() {
        var hooks = mock(ApplicationLifecycle.ShutdownHooks.class);
        doThrow(new IllegalStateException("JVM is shutting down")).when(hooks).remove(any());
        var lifecycle = new ApplicationLifecycle(new ResourceCleanup(), hooks);
        lifecycle.installShutdownHook();
        assertDoesNotThrow(lifecycle::close);
    }

    @Test
    void resourceRegisteredAfterCloseShouldBeReleasedAndRejected() {
        ResourceCleanup resources = new ResourceCleanup();
        resources.close();
        AtomicInteger closed = new AtomicInteger();
        assertThrows(IllegalStateException.class, () -> resources.register(STT, closed::incrementAndGet));
        resources.close();
        assertEquals(1, closed.get());
    }

    @Test
    void latePresentationBundleShouldBeReleasedInDependencyOrder() {
        ResourceCleanup resources = new ResourceCleanup();
        resources.close();
        List<ResourceCleanup.Resource> closed = new ArrayList<>();
        assertThrows(IllegalStateException.class, () -> resources.registerAll(Map.of(
                JAVAFX_RUNTIME, () -> closed.add(JAVAFX_RUNTIME),
                VISUAL_OUTPUT, () -> closed.add(VISUAL_OUTPUT),
                CORE_VISUAL, () -> closed.add(CORE_VISUAL))));
        assertEquals(List.of(CORE_VISUAL, VISUAL_OUTPUT, JAVAFX_RUNTIME), closed);
    }
}

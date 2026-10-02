package com.fuad.bootstrap;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;

/** One shutdown path shared by normal termination and the JVM hook. */
final class ApplicationLifecycle implements AutoCloseable {
    interface ShutdownHooks {
        void add(Thread hook);
        void remove(Thread hook);
    }

    private final ResourceCleanup resources;
    private final ShutdownHooks hooks;
    private final CountDownLatch stopped = new CountDownLatch(1);
    private final Thread shutdownHook = new Thread(this::close, "ares-shutdown");
    private boolean hookInstalled;
    private boolean closing;

    ApplicationLifecycle() {
        this(new ResourceCleanup(), new ShutdownHooks() {
            @Override public void add(Thread hook) { Runtime.getRuntime().addShutdownHook(hook); }
            @Override public void remove(Thread hook) { Runtime.getRuntime().removeShutdownHook(hook); }
        });
    }

    ApplicationLifecycle(ResourceCleanup resources, ShutdownHooks hooks) {
        this.resources = Objects.requireNonNull(resources);
        this.hooks = Objects.requireNonNull(hooks);
    }

    ResourceCleanup resources() {
        return resources;
    }

    synchronized void installShutdownHook() {
        if (closing) throw new IllegalStateException("Application is closing");
        if (!hookInstalled) {
            hooks.add(shutdownHook);
            hookInstalled = true;
        }
    }

    void awaitShutdown() throws InterruptedException {
        stopped.await();
    }

    @Override
    public void close() {
        synchronized (this) {
            closing = true;
        }
        try {
            // Concurrent callers wait in ResourceCleanup until the owner finishes closing.
            resources.close();
        }
        finally {
            stopped.countDown();
            synchronized (this) {
                if (hookInstalled && Thread.currentThread() != shutdownHook) {
                    try {
                        hooks.remove(shutdownHook);
                    }
                    catch (IllegalStateException ignored) {
                        // JVM shutdown has already started; the hook uses the same cleanup.
                    }
                    hookInstalled = false;
                }
            }
        }
    }
}

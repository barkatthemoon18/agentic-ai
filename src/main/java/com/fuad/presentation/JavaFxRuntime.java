package com.fuad.presentation;

import javafx.application.Platform;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public final class JavaFxRuntime implements AutoCloseable {
    private static final AtomicBoolean START_REQUESTED = new AtomicBoolean(false);
    private static final CompletableFuture<Void> READY = new CompletableFuture<>();
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public JavaFxRuntime() {
        ensureToolkit();
    }

    public void runLater(Runnable runnable) {
        if (closed.get()) {
            throw new IllegalStateException("JavaFX runtime is closed");
        }
        if (Platform.isFxApplicationThread()) {
            runnable.run();
        }
        else {
            Platform.runLater(runnable);
        }
    }

    public void runAndWait(Runnable runnable) {
        if (Platform.isFxApplicationThread()) {
            runnable.run();
            return;
        }
        if (closed.get()) {
            throw new IllegalStateException("JavaFX runtime is closed");
        }
        CompletableFuture<Void> completion = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                runnable.run();
                completion.complete(null);
            }
            catch (Throwable failure) {
                completion.completeExceptionally(failure);
            }
        });
        completion.join();
    }

    private static void ensureToolkit() {
        if (START_REQUESTED.compareAndSet(false, true)) {
            Runnable markReady = () -> {
                Platform.setImplicitExit(false);
                READY.complete(null);
            };
            try {
                Platform.startup(markReady);
            }
            catch (IllegalStateException e) {
                try {
                    Platform.runLater(markReady);
                }
                catch (RuntimeException secondFailure) {
                    READY.completeExceptionally(secondFailure);
                }
            }
            catch (RuntimeException e) {
                READY.completeExceptionally(e);
            }
        }
        READY.join();
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            Platform.exit();
        }
    }
}

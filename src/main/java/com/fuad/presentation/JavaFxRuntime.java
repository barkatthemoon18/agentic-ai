package com.fuad.presentation;

import javafx.application.Platform;
import javafx.scene.text.Font;

import java.net.URL;
import java.util.Objects;
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
                loadUiFonts();
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

    private static void loadUiFonts() {
        loadFont("/fonts/Inter-Medium.ttf");
        loadFont("/fonts/Inter-Regular.ttf");
        loadFont("/fonts/Inter-SemiBold.ttf");
        loadFont("/fonts/JetBrainsMono-Bold.ttf");
        loadFont("/fonts/JetBrainsMono-Regular.ttf");
    }

    private static void loadFont(String resourcePath) {
        try (var input = JavaFxRuntime.class.getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IllegalStateException("Missing UI font resource: " + resourcePath);
            }
            Font font = Font.loadFont(input, 12.0);
            if (font == null) {
                throw new IllegalStateException("JavaFX could not load UI font: " + resourcePath);
            }
            System.out.printf("Loaded UI font: family='%s', name'%s', style='%s'%n", font.getFamily(), font.getName(),
                    font.getStyle());
        }
        catch (Exception e) {
            throw new IllegalStateException("Unable to read UI font: " + resourcePath, e);
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            Platform.exit();
        }
    }
}

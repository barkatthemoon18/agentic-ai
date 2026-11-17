package com.fuad.presentation.keyboard;

import com.fuad.presentation.JavaFxRuntime;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.transform.Scale;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

/**
 * Interactive contextual keyboard preview. F8 pauses or resumes edge reflections.
 * Snapshots accept {@code --mode QUERY|MEDIA|FILES|SYSTEM}, {@code --query "..."},
 * {@code --width}, {@code --height}, and a deterministic {@code --time} in seconds.
 */
public final class AresKeyboardVisualDemo {

    private AresKeyboardVisualDemo() {
    }

    public static void main(String[] args) throws InterruptedException {
        DemoOptions options = DemoOptions.parse(args);
        CountDownLatch closed = new CountDownLatch(1);
        try (JavaFxRuntime runtime = new JavaFxRuntime()) {
            runtime.runAndWait(() -> {
                Rectangle2D screen = Screen.getPrimary().getVisualBounds();
                double initialScale = Math.min(1, Math.min(
                        Math.max(1, screen.getWidth() - 80) / AresKeyboardView.WIDTH,
                        Math.max(1, screen.getHeight() - 120) / AresKeyboardView.HEIGHT));
                double width = options.width() == null
                        ? AresKeyboardView.WIDTH * (options.snapshot() == null ? initialScale : 1)
                        : options.width();
                double height = options.height() == null
                        ? AresKeyboardView.HEIGHT * (options.snapshot() == null ? initialScale : 1)
                        : options.height();
                if (options.width() != null && options.height() == null) {
                    height = width * AresKeyboardView.HEIGHT / AresKeyboardView.WIDTH;
                }
                if (options.height() != null && options.width() == null) {
                    width = height * AresKeyboardView.WIDTH / AresKeyboardView.HEIGHT;
                }

                AresKeyboardView keyboard = new AresKeyboardView();
                keyboard.setMode(options.mode());
                keyboard.setQuery(options.query());
                Pane viewport = createViewport(keyboard);
                viewport.setStyle("-fx-background-color: transparent;");
                Scene scene = new Scene(viewport, width, height, Color.TRANSPARENT);
                // F8 freezes or resumes the traces while inspecting the visual demo.
                scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                    if (event.getCode() == KeyCode.F8) {
                        keyboard.setAnimationsEnabled(!keyboard.areAnimationsEnabled());
                        event.consume();
                    }
                });
                Stage stage = new Stage(StageStyle.TRANSPARENT);
                stage.setTitle("ARES // TOUCH SURFACE - Contextual v2");
                stage.setScene(scene);
                stage.setMinWidth(Math.min(800, width));
                stage.setMinHeight(Math.min(400, height));
                stage.setOnHidden(event -> closed.countDown());
                if (options.snapshot() == null) {
                    stage.show();
                    stage.centerOnScreen();
                } else {
                    try {
                        viewport.resize(width, height);
                        viewport.applyCss();
                        viewport.layout();
                        keyboard.setAnimationTime(Duration.seconds(options.timeSeconds()));
                        writeSnapshot(scene, options.snapshot(), (int) Math.ceil(width), (int) Math.ceil(height));
                    } catch (IOException e) {
                        throw new UncheckedIOException("Unable to write keyboard snapshot", e);
                    } finally {
                        stage.close();
                        closed.countDown();
                    }
                }
            });
            closed.await();
        }
    }

    private static Pane createViewport(AresKeyboardView keyboard) {
        Scale scale = new Scale(1, 1, 0, 0);
        keyboard.getTransforms().add(scale);
        keyboard.setManaged(false);
        return new Pane(keyboard) {
            @Override
            protected void layoutChildren() {
                double factor = Math.min(getWidth() / AresKeyboardView.WIDTH,
                        getHeight() / AresKeyboardView.HEIGHT);
                scale.setX(factor);
                scale.setY(factor);
                keyboard.setLayoutX((getWidth() - AresKeyboardView.WIDTH * factor) / 2);
                keyboard.setLayoutY((getHeight() - AresKeyboardView.HEIGHT * factor) / 2);
            }
        };
    }

    private static void writeSnapshot(Scene scene, Path destination, int width, int height) throws IOException {
        // Prime glyph and vector-effect caches before exporting an offscreen scene.
        scene.snapshot(null);
        WritableImage image = scene.snapshot(new WritableImage(width, height));
        BufferedImage buffered = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int[] pixels = new int[width];
        for (int y = 0; y < height; y++) {
            image.getPixelReader().getPixels(0, y, width, 1,
                    javafx.scene.image.PixelFormat.getIntArgbInstance(), pixels, 0, width);
            buffered.setRGB(0, y, width, 1, pixels, 0, width);
        }
        Path absolute = destination.toAbsolutePath();
        Files.createDirectories(absolute.getParent());
        if (!ImageIO.write(buffered, "png", absolute.toFile())) {
            throw new IOException("No PNG image writer is available");
        }
    }

    private record DemoOptions(Path snapshot, Double width, Double height, double timeSeconds,
                               String mode, String query) {
        private static DemoOptions parse(String[] args) {
            Path snapshot = null;
            Double width = null;
            Double height = null;
            double timeSeconds = 0;
            String mode = "QUERY";
            String query = "";
            for (int i = 0; i < args.length; i++) {
                String option = args[i];
                if (++i >= args.length) {
                    throw new IllegalArgumentException("Missing value for " + option);
                }
                switch (option) {
                    case "--snapshot" -> snapshot = Path.of(args[i]);
                    case "--width" -> width = dimension(args[i]);
                    case "--height" -> height = dimension(args[i]);
                    case "--time" -> timeSeconds = animationTime(args[i]);
                    case "--mode" -> mode = mode(args[i]);
                    case "--query" -> query = args[i];
                    default -> throw new IllegalArgumentException("Unknown option: " + option);
                }
            }
            return new DemoOptions(snapshot, width, height, timeSeconds, mode, query);
        }

        private static String mode(String value) {
            String mode = value.toUpperCase(Locale.ROOT);
            if (!Set.of("QUERY", "MEDIA", "FILES", "SYSTEM").contains(mode)) {
                throw new IllegalArgumentException("Mode must be QUERY, MEDIA, FILES or SYSTEM");
            }
            return mode;
        }

        private static double animationTime(String value) {
            double seconds;
            try {
                seconds = Double.parseDouble(value);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Animation time must be a finite non-negative number of seconds", e);
            }
            if (!Double.isFinite(seconds * 1000) || seconds < 0) {
                throw new IllegalArgumentException("Animation time must be a finite non-negative number of seconds");
            }
            return seconds;
        }

        private static double dimension(String value) {
            double dimension = Double.parseDouble(value);
            if (!Double.isFinite(dimension) || dimension < 1 || dimension > 8192) {
                throw new IllegalArgumentException("Dimensions must be between 1 and 8192 pixels");
            }
            return dimension;
        }
    }
}

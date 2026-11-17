package com.fuad.presentation.keyboard;

import com.fuad.pipeline.VoiceInputController;
import com.fuad.presentation.JavaFxRuntime;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.transform.Scale;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;


public final class AresKeyboardOverlay implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(AresKeyboardOverlay.class);
    private static final double MAX_WIDTH = 760;
    private static final double EDGE_MARGIN = 18;
    private static final double IDLE_OPACITY = 0.16;
    private static final Duration IDLE_DELAY = Duration.seconds(2);
    private static final Duration FADE_DURATION = javafx.util.Duration.millis(190);
    private final JavaFxRuntime runtime;
    private final VoiceInputController input;
    private final Runnable focusDeck;
    private final AutoCloseable subscription;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private Stage stage;
    private AresKeyboardView visual;
    private Pane root;
    private PauseTransition idleDelay;
    private FadeTransition fade;
    private boolean hovering;

    public AresKeyboardOverlay(JavaFxRuntime runtime, VoiceInputController input) {
        this(runtime, input, null);
    }

    public AresKeyboardOverlay(JavaFxRuntime runtime, VoiceInputController input, Runnable focusDeck) {
        this.runtime = Objects.requireNonNull(runtime);
        this.input = Objects.requireNonNull(input);
        this.focusDeck = focusDeck;

        runtime.runAndWait(this::createStage);
        subscription = input.subscribe(b -> onMuteChange());
        onMuteChange();
    }

    /** Called on the JavaFX thread when the user explicitly moves to the Command Deck. */
    public void yieldToDeck(Rectangle2D deckBounds) {
        Objects.requireNonNull(deckBounds);
        if (closed.get() || !stage.isShowing()) {
            return;
        }
        Rectangle2D keyboardBounds = new Rectangle2D(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight());
        if (!keyboardBounds.intersects(deckBounds)) {
            return;
        }
        hovering = false;
        stage.hide();
        idleDelay.stop();
        if (fade != null) {
            fade.stop();
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            subscription.close();
        }
        catch (Exception e) {
            LOGGER.warn("Unable to unsubscribe keyboard listener", e);
        }
        runtime.runAndWait(() -> {
            if (idleDelay != null) {
                idleDelay.stop();
            }
            if (fade != null) {
                fade.stop();
            }
            if (stage != null) {
                stage.close();
            }
        });
    }

    private void createStage() {
        Rectangle2D primary = Screen.getPrimary().getVisualBounds();
        double scale = Math.min(1, Math.min(MAX_WIDTH / AresKeyboardView.WIDTH,
                Math.min((primary.getWidth() - EDGE_MARGIN * 2) / AresKeyboardView.WIDTH,
                        (primary.getHeight() - EDGE_MARGIN * 2) / AresKeyboardView.HEIGHT)));
        double width = AresKeyboardView.WIDTH * scale;
        double height = AresKeyboardView.HEIGHT * scale;
        visual = new AresKeyboardView(() -> input.setMuted(false), focusDeck);
        visual.getTransforms().add(new Scale(scale, scale, 0, 0));

        root = new Pane(visual);
        root.setStyle("-fx-background-color: transparent;");
        root.setPrefSize(width, height);
        root.setMinSize(width, height);
        root.setMaxSize(width, height);
        root.setPickOnBounds(false);
        root.setOnMouseEntered(event -> {
            hovering = true;
            idleDelay.stop();
            animateOpacity(1.0);
        });
        root.setOnMouseExited(event -> {
            hovering = false;
            scheduleIdle();
        });
        idleDelay = new PauseTransition(IDLE_DELAY);
        idleDelay.setOnFinished(event -> {
            if (!hovering && stage.isShowing()) {
                animateOpacity(IDLE_OPACITY);
            }
        });
        stage = new Stage(StageStyle.TRANSPARENT);
        stage.setTitle(WindowsKeyboardWindowSupport.WINDOW_TITLE);
        stage.setResizable(false);
        stage.setAlwaysOnTop(true);
        stage.setX(primary.getMaxX() - width - EDGE_MARGIN);
        stage.setY(primary.getMaxY() - height - EDGE_MARGIN);

        Scene scene = new Scene(root, width, height);
        scene.setFill(Color.TRANSPARENT);

        stage.setScene(scene);
        stage.focusedProperty().addListener((observable, previous, focused) -> {
            if (!focused && !hovering) {
                scheduleIdle();
            }
        });
    }

    private void onMuteChange() {
        if (closed.get()) {
            return;
        }
        try {
            runtime.runLater(() -> {
                if (!closed.get()) {
                    applyMuted(input.isMuted());
                }
            });
        }
        catch (IllegalStateException e) {
            LOGGER.warn("Runtime was already closed", e);
        }
    }

    private void applyMuted(boolean muted) {
        visual.setVoiceMuted(muted);
        if (!muted) {
            idleDelay.stop();
            if (fade != null) {
                fade.stop();
            }
            stage.hide();
            hovering = false;
            return;
        }
        if (!stage.isShowing()) {
            root.setOpacity(1.0);
            long previousForeground = WindowsKeyboardWindowSupport.captureForegroundWindow();
            stage.show();
            WindowsKeyboardWindowSupport.tryConfigureNonActivating(previousForeground);
            scheduleIdle();
            LOGGER.debug("ARES virtual keyboard shown on primary display");
        }
    }

    private void scheduleIdle() {
        idleDelay.playFromStart();
    }

    private void animateOpacity(double opacity) {
        if (fade != null) {
            fade.stop();
        }
        fade = new FadeTransition(FADE_DURATION, root);
        fade.setToValue(opacity);
        fade.play();
    }
}

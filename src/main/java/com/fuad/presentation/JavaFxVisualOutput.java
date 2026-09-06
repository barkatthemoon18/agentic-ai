package com.fuad.presentation;

import com.fuad.audio.AssistantAudioSnapshot;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.robot.Robot;
import javafx.scene.shape.Polygon;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.net.URL;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class JavaFxVisualOutput implements VisualOutput {
    private static final double OVERLAY_WIDTH = 440.0;
    private static final double MINIMUM_HEIGHT = 132.0;
    private static final double MAXIMUM_HEIGHT = 360.0;
    private static final double SCREEN_MARGIN = 24.0;
    private static final double CORNER_CUT = 14.0;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final AtomicBoolean TOOLKIT_START_REQUESTED = new AtomicBoolean(false);
    private static final CompletableFuture<Void> TOOLKIT_READY = new CompletableFuture<>();
    private final AtomicBoolean closed =  new AtomicBoolean(false);
    private Stage stage;
    private StackPane overlayRoot;
    private Polygon frame;
    private Label statusLabel;
    private Label messageLabel;
    private Label timeLabel;
    private ScrollPane messageScroll;
    private PauseTransition dismissTimer;
    private Animation activeAnimation;

    public JavaFxVisualOutput() {
        ensureToolkit();
        runAndWait(this::createOverlay);
    }

    @Override
    public void show(VisualMessage visualMessage) {
        Objects.requireNonNull(visualMessage, "visualMessage must not be null");
        if (closed.get()) {
            throw new IllegalStateException("Visual output is already closed");
        }
        runLater(() -> showInternal(visualMessage));
    }

    @Override
    public void hide() {
        if (closed.get()) {
            return;
        }
        runLater(this::hideInternal);
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        runLater(() -> {
            stopAnimations();
            if (stage != null) {
                stage.hide();
                stage.close();
            }
            Platform.exit();
        });
    }

    private static void ensureToolkit() {
        if (TOOLKIT_START_REQUESTED.compareAndSet(false, true)) {
            Runnable markAsReady = () -> {
                Platform.setImplicitExit(false);
                TOOLKIT_READY.complete(null);
            };
            try {
                Platform.startup(markAsReady);
            }
            catch (IllegalStateException e) {
                try {
                    Platform.runLater(markAsReady);
                }
                catch (RuntimeException e2) {
                    TOOLKIT_READY.completeExceptionally(e2);
                }
            }
            catch (RuntimeException e) {
                TOOLKIT_READY.completeExceptionally(e);
            }
        }
        TOOLKIT_READY.join();
    }

    private void createOverlay() {
        statusLabel = new Label();
        statusLabel.getStyleClass().add("ares-status");

        Label titleLabel = new Label("ARES // RESPONSE");
        titleLabel.getStyleClass().add("ares-title");

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        HBox header = new HBox(12.0, titleLabel, headerSpacer, statusLabel);
        header.setAlignment(Pos.CENTER_LEFT);

        Region separator = new Region();
        separator.getStyleClass().add("ares-separator");
        separator.setMinHeight(1.0);
        separator.setPrefHeight(1.0);
        separator.setMaxHeight(1.0);

        Label iconLabel = new Label("\u224B");
        iconLabel.getStyleClass().add("ares-icon");

        StackPane iconContainer = new StackPane(iconLabel);
        iconContainer.getStyleClass().add("ares-icon-container");
        iconContainer.setMinSize(42.0, 42.0);
        iconContainer.setPrefSize(42.0, 42.0);
        iconContainer.setMaxSize(42.0, 42.0);

        messageLabel = new Label();
        messageLabel.getStyleClass().add("ares-message");
        messageLabel.setWrapText(true);
        messageLabel.setMaxWidth(Double.MAX_VALUE);

        messageScroll = new ScrollPane(messageLabel);
        messageScroll.getStyleClass().add("ares-message-scroll");
        messageScroll.setFitToWidth(true);
        messageScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        messageScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        messageScroll.setPannable(true);
        messageScroll.setMaxHeight(230.0);

        HBox.setHgrow(messageScroll, Priority.ALWAYS);

        HBox body = new HBox(16.0, iconContainer, messageScroll);
        body.setAlignment(Pos.TOP_LEFT);
        VBox.setVgrow(body, Priority.ALWAYS);

        timeLabel = new Label();
        timeLabel.getStyleClass().add("ares-time");

        HBox footer = new HBox(timeLabel);
        footer.setAlignment(Pos.CENTER_RIGHT);

        VBox content = new VBox(
                13.0,
                header,
                separator,
                body,
                footer
        );
        content.getStyleClass().add("ares-content");
        content.setPadding(new Insets(18.0, 20.0, 15.0, 20.0));
        content.setPrefWidth(OVERLAY_WIDTH);
        content.setMaxWidth(Double.MAX_VALUE);

        frame = new Polygon();
        frame.getStyleClass().add("ares-frame");
        frame.setManaged(false);
        frame.setMouseTransparent(true);
        frame.setEffect(new DropShadow(18.0, Color.rgb(34, 207, 245, 0.34)));

        overlayRoot = new StackPane(frame, content);
        overlayRoot.getStyleClass().add("ares-root");
        overlayRoot.setPrefWidth(OVERLAY_WIDTH);
        overlayRoot.setMinHeight(MINIMUM_HEIGHT);
        overlayRoot.setMaxHeight(MAXIMUM_HEIGHT);

        overlayRoot.widthProperty().addListener((observable, oldValue, newValue) ->
                updateFrameShape());

        overlayRoot.heightProperty().addListener((observable, oldValue, newValue) ->
                        updateFrameShape());

        Scene scene = new Scene(overlayRoot);
        scene.setFill(Color.TRANSPARENT);

        URL stylesheet = JavaFxVisualOutput.class.getResource("/ui/response.css");

        if (stylesheet == null) {
            throw new IllegalStateException("Missing resource: /ui/response.css");
        }

        scene.getStylesheets().add(stylesheet.toExternalForm());

        stage = new Stage(StageStyle.TRANSPARENT);
        stage.setTitle("Ares Response");
        stage.setAlwaysOnTop(true);
        stage.setResizable(false);
        stage.setScene(scene);

        dismissTimer = new PauseTransition();
        dismissTimer.setOnFinished(
                event -> hideInternal()
        );

        overlayRoot.applyCss();
        overlayRoot.layout();
        updateFrameShape();
    }

    private void showInternal(VisualMessage visualMessage) {
        stopAnimations();
        AssistantAudioSnapshot audioSnapshot = visualMessage.getAudioSnapshot();
        messageLabel.setText(visualMessage.getText());
        statusLabel.setText(buildStatusText(audioSnapshot));
        timeLabel.setText(LocalTime.now().format(TIME_FORMATTER));
        messageScroll.setVvalue(0.0);
        overlayRoot.setOpacity(0.0);
        overlayRoot.setTranslateX(20.0);
        if (!stage.isShowing()) {
            stage.show();
        }
        overlayRoot.applyCss();
        overlayRoot.layout();
        stage.sizeToScene();

        double effectiveHeight = Math.max(MINIMUM_HEIGHT, Math.min(MAXIMUM_HEIGHT, stage.getHeight()));

        stage.setWidth(OVERLAY_WIDTH);
        stage.setHeight(effectiveHeight);

        positionStage();
        updateFrameShape();

        FadeTransition fade = new FadeTransition(Duration.millis(180), overlayRoot);
        fade.setFromValue(0.0);
        fade.setToValue(1.0);

        TranslateTransition movement = new TranslateTransition(Duration.millis(180), overlayRoot);
        movement.setFromX(20.0);
        movement.setToX(0.0);

        ParallelTransition entrance = new ParallelTransition(fade, movement);
        activeAnimation = entrance;

        entrance.setOnFinished(event -> {
            activeAnimation = null;
            startDismissTimer(visualMessage.getText().length());
        });
        entrance.play();
    }

    private void hideInternal() {
        if (stage == null || !stage.isShowing()) {
            return;
        }
        stopAnimations();
        FadeTransition fade = new FadeTransition(Duration.millis(140), overlayRoot);
        fade.setFromValue(overlayRoot.getOpacity());
        fade.setToValue(0.0);

        TranslateTransition movement = new TranslateTransition(Duration.millis(140), overlayRoot);
        movement.setFromX(overlayRoot.getTranslateX());
        movement.setToX(10.0);

        ParallelTransition exit = new ParallelTransition(fade, movement);
        activeAnimation = exit;

        exit.setOnFinished(event -> {
            activeAnimation = null;
            stage.hide();
            overlayRoot.setOpacity(1.0);
            overlayRoot.setTranslateX(0.0);
        });
        exit.play();
    }

    private void startDismissTimer(int charCount) {
        dismissTimer.stop();
        dismissTimer.setDuration(Duration.seconds(calculateDisplaySeconds(charCount)));
        dismissTimer.playFromStart();
    }

    private void stopAnimations() {
        if (dismissTimer != null) {
            dismissTimer.stop();
        }
        if (activeAnimation != null) {
            activeAnimation.stop();
            activeAnimation = null;
        }
    }

    private void positionStage() {
        Screen targetScreen = resolveTargetScreen();
        Rectangle2D bounds = targetScreen.getVisualBounds();

        stage.setX(bounds.getMaxX() - stage.getWidth() - SCREEN_MARGIN);
        stage.setY(bounds.getMaxY() - stage.getHeight() - SCREEN_MARGIN);
    }

    private Screen resolveTargetScreen() {
        try {
            Robot robot = new Robot();

            double pointerX = robot.getMouseX();
            double pointerY = robot.getMouseY();

            List<Screen> screens = Screen.getScreensForRectangle(pointerX, pointerY, 1.0, 1.0);
            if (!screens.isEmpty()) {
                return screens.getFirst();
            }
        }
        catch (Exception e) {
            System.err.println("Unable to resolve pointer screen: " + e.getMessage());
        }
        return Screen.getPrimary();
    }

    private void updateFrameShape() {
        if (frame == null || overlayRoot == null) {
            return;
        }
        double width = overlayRoot.getWidth();
        double height = overlayRoot.getHeight();

        if (width <= 0.0 || height <= 0.0) {
            return;
        }

        frame.getPoints().setAll(calculateFramePoints(width, height, CORNER_CUT));
    }

    static String buildStatusText(AssistantAudioSnapshot audioSnapshot) {
        Objects.requireNonNull(audioSnapshot, "audioSnapshot must not be null");
        return audioSnapshot.isMuted()
                ? "\u25CF  MUTED"
                : "\u25CF  VOL " + audioSnapshot.getVolume() + "%";
    }

    static double calculateDisplaySeconds(int charCount) {
        if (charCount < 0) {
            throw new IllegalArgumentException("Character count must not be negative");
        }
        double seconds = Math.ceil(charCount / 18.0) + 4.0;
        return Math.clamp(seconds, 6.0, 30.0);
    }

    static List<Double> calculateFramePoints(double width, double height, double cornerCut) {
        if (width <= 0.0 || height <= 0.0) {
            throw new IllegalArgumentException("Frame dimensions must be positive");
        }
        if (cornerCut <= 0.0 || cornerCut * 2.0 > Math.min(width, height)) {
            throw new IllegalArgumentException("Corner cut does not fit inside the frame");
        }
        return List.of(
                cornerCut, 0.0,
                width - cornerCut, 0.0,
                width, cornerCut,
                width, height - cornerCut,
                width - cornerCut, height,
                cornerCut, height,
                0.0, height - cornerCut,
                0.0, cornerCut
        );
    }

    private static void runAndWait(Runnable runnable) {
        if (Platform.isFxApplicationThread()) {
            runnable.run();
            return;
        }
        CompletableFuture<Void> completableFuture = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                runnable.run();
                completableFuture.complete(null);
            }
            catch (Exception e) {
                completableFuture.completeExceptionally(e);
            }
        });
        completableFuture.join();
    }

    private static void runLater(Runnable runnable) {
        if (Platform.isFxApplicationThread()) {
            runnable.run();
        }
        else {
            Platform.runLater(runnable);
        }
    }
}

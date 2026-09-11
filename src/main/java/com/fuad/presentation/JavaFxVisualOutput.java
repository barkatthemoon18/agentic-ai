package com.fuad.presentation;

import com.fuad.audio.AssistantAudioSnapshot;
import com.fuad.assistant.skills.os.ApplicationCatalogPayload;
import com.fuad.assistant.skills.os.CatalogNavigation;
import com.fuad.assistant.skills.os.CatalogSessionStore;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.robot.Robot;
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
    private static final double OVERLAY_WIDTH = 560.0;
    private static final double MINIMUM_HEIGHT = 132.0;
    private static final double SCREEN_MARGIN = 24.0;
    private static final double HALO_PADDING = 24.0;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final AtomicBoolean TOOLKIT_START_REQUESTED = new AtomicBoolean(false);
    private static final CompletableFuture<Void> TOOLKIT_READY = new CompletableFuture<>();
    private final AtomicBoolean closed =  new AtomicBoolean(false);
    private Stage stage;
    private StackPane overlayRoot;
    private Label statusLabel;
    private Label messageLabel;
    private Label timeLabel;
    private ScrollPane messageScroll;
    private VBox catalogPane;
    private ListView<String> catalogList;
    private TextField catalogSearch;
    private Label catalogPageLabel;
    private Button catalogPrevious;
    private Button catalogNext;
    private PauseTransition dismissTimer;
    private Animation activeAnimation;
    private final CatalogSessionStore catalogSessions;
    private final AtomicBoolean updatingCatalog = new AtomicBoolean(false);
    private AutoCloseable catalogSubscription;

    public JavaFxVisualOutput() {
        this(new CatalogSessionStore());
    }

    public JavaFxVisualOutput(CatalogSessionStore catalogSessions) {
        this.catalogSessions = Objects.requireNonNull(catalogSessions, "catalogSessions must not be null");
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
        closeCatalogSubscription();
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
        iconContainer.setMinSize(28.0, 28.0);
        iconContainer.setPrefSize(28.0, 28.0);
        iconContainer.setMaxSize(28.0, 28.0);

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
        messageScroll.setMinSize(0.0, 0.0);
        messageScroll.setMaxHeight(Double.MAX_VALUE);
        messageLabel.setMinHeight(Region.USE_PREF_SIZE);

        createCatalogPane();
        catalogPane.setVisible(false);
        catalogPane.setManaged(false);

        StackPane responseContent = new StackPane(messageScroll, catalogPane);
        HBox.setHgrow(responseContent, Priority.ALWAYS);

        HBox body = new HBox(10.0, iconContainer, responseContent);
        body.setAlignment(Pos.TOP_LEFT);
        VBox.setVgrow(body, Priority.ALWAYS);

        timeLabel = new Label();
        timeLabel.getStyleClass().add("ares-time");

        HBox footer = new HBox(timeLabel);
        footer.setAlignment(Pos.CENTER_RIGHT);

        VBox content = new VBox(
                8.0,
                header,
                separator,
                body,
                footer
        );
        content.getStyleClass().addAll("ares-content", "ares-frame");
        content.setMinWidth(0.0);
        content.setPadding(new Insets(12.0, 20.0, 12.0, 20.0));
        content.setPrefWidth(OVERLAY_WIDTH);
        content.setMaxWidth(Double.MAX_VALUE);

        overlayRoot = new StackPane(content);
        overlayRoot.getStyleClass().add("ares-root");
        overlayRoot.setPadding(new Insets(HALO_PADDING));
        overlayRoot.setPrefWidth(OVERLAY_WIDTH);
        overlayRoot.setMinSize(0.0, 0.0);

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
        overlayRoot.hoverProperty().addListener((observable, wasHovered, hovered) -> {
            if (hovered && dismissTimer.getStatus() == Animation.Status.RUNNING) {
                dismissTimer.pause();
            }
            else if (!hovered && dismissTimer.getStatus() == Animation.Status.PAUSED) {
                dismissTimer.play();
            }
        });

        overlayRoot.applyCss();
        overlayRoot.layout();
    }

    private void showInternal(VisualMessage visualMessage) {
        stopAnimations();
        AssistantAudioSnapshot audioSnapshot = visualMessage.getAudioSnapshot();
        boolean catalog = visualMessage.getPayload() instanceof ApplicationCatalogPayload;
        if (catalog) {
            showCatalog((ApplicationCatalogPayload) visualMessage.getPayload());
        }
        else {
            closeCatalogSubscription();
            catalogPane.setVisible(false);
            catalogPane.setManaged(false);
            messageScroll.setVisible(true);
            messageScroll.setManaged(true);
            messageLabel.setText(visualMessage.getText());
        }
        statusLabel.setText(buildStatusText(audioSnapshot));
        timeLabel.setText(LocalTime.now().format(TIME_FORMATTER));
        messageScroll.setVvalue(0.0);
        overlayRoot.setOpacity(0.0);
        overlayRoot.setTranslateX(20.0);
        Rectangle2D screenBounds = resolveTargetScreen().getVisualBounds();
        sizeOverlay(screenBounds, catalog);
        if (!stage.isShowing()) {
            stage.show();
        }

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
            if (!catalog) startDismissTimer(visualMessage.getText().length());
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
            closeCatalogSubscription();
            overlayRoot.setOpacity(1.0);
            overlayRoot.setTranslateX(0.0);
        });
        exit.play();
    }

    private void startDismissTimer(int charCount) {
        dismissTimer.stop();
        dismissTimer.setDuration(Duration.seconds(calculateDisplaySeconds(charCount)));
        dismissTimer.playFromStart();
        if (overlayRoot.isHover()) {
            dismissTimer.pause();
        }
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

    private void sizeOverlay(Rectangle2D screenBounds, boolean catalog) {
        if (catalog) {
            Rectangle2D bounds = calculateOverlayBounds(screenBounds, 520.0);
            overlayRoot.setPrefSize(bounds.getWidth(), bounds.getHeight());
            stage.setWidth(bounds.getWidth());
            stage.setHeight(bounds.getHeight());
            stage.setX(bounds.getMinX());
            stage.setY(bounds.getMinY());
            overlayRoot.resize(bounds.getWidth(), bounds.getHeight());
            overlayRoot.layout();
            return;
        }
        Rectangle2D maximum = calculateOverlayBounds(screenBounds, Double.MAX_VALUE);
        // Measure without a scrollbar so a previous long response cannot affect wrapping.
        messageScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        overlayRoot.setPrefHeight(Region.USE_COMPUTED_SIZE);
        overlayRoot.applyCss();
        overlayRoot.resize(maximum.getWidth(), maximum.getHeight());
        overlayRoot.layout();
        double textWidth = Math.max(1.0, messageScroll.getViewportBounds().getWidth());
        Insets scrollInsets = messageScroll.getInsets();
        double textHeight = Math.ceil(messageLabel.prefHeight(textWidth));
        messageScroll.setPrefHeight(textHeight + scrollInsets.getTop() + scrollInsets.getBottom());
        double preferredHeight = Math.ceil(overlayRoot.prefHeight(maximum.getWidth()));
        Rectangle2D bounds = calculateOverlayBounds(screenBounds, preferredHeight);
        messageScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        overlayRoot.setPrefSize(bounds.getWidth(), bounds.getHeight());
        overlayRoot.resize(bounds.getWidth(), bounds.getHeight());
        stage.setWidth(bounds.getWidth());
        stage.setHeight(bounds.getHeight());
        stage.setX(bounds.getMinX());
        stage.setY(bounds.getMinY());
        overlayRoot.layout();
    }

    private void createCatalogPane() {
        catalogSearch = new TextField();
        catalogSearch.setPromptText("Buscar aplicaciones");
        catalogSearch.getStyleClass().add("ares-catalog-search");
        catalogList = new ListView<>();
        catalogList.getStyleClass().add("ares-catalog-list");
        catalogPageLabel = new Label();
        catalogPageLabel.getStyleClass().add("ares-catalog-page");
        catalogPrevious = new Button("Anterior");
        catalogNext = new Button("Siguiente");
        catalogPrevious.getStyleClass().add("ares-catalog-button");
        catalogNext.getStyleClass().add("ares-catalog-button");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox controls = new HBox(8.0, catalogPrevious, spacer, catalogPageLabel, catalogNext);
        controls.setAlignment(Pos.CENTER);
        catalogPane = new VBox(10.0, catalogSearch, catalogList, controls);
        VBox.setVgrow(catalogList, Priority.ALWAYS);
    }

    private void showCatalog(ApplicationCatalogPayload initial) {
        closeCatalogSubscription();
        messageScroll.setVisible(false);
        messageScroll.setManaged(false);
        catalogPane.setVisible(true);
        catalogPane.setManaged(true);
        renderCatalog(initial);
        catalogSearch.setOnAction(event -> {
            if (!updatingCatalog.get()) catalogSessions.filter(initial.sessionId(), catalogSearch.getText());
        });
        catalogPrevious.setOnAction(event -> catalogSessions.navigate(initial.sessionId(), CatalogNavigation.PREVIOUS));
        catalogNext.setOnAction(event -> catalogSessions.navigate(initial.sessionId(), CatalogNavigation.NEXT));
        catalogSubscription = catalogSessions.observe(initial.sessionId(), payload -> runLater(() -> renderCatalog(payload)));
    }

    private void renderCatalog(ApplicationCatalogPayload payload) {
        updatingCatalog.set(true);
        try {
            if (!Objects.equals(catalogSearch.getText(), payload.filter())) catalogSearch.setText(payload.filter());
            catalogList.getItems().setAll(payload.items().stream().map(item -> item.displayName()).toList());
            catalogPageLabel.setText("Página " + (payload.pageIndex() + 1) + " de " + payload.totalPages()
                    + " · " + payload.totalCount());
            catalogPrevious.setDisable(payload.pageIndex() == 0);
            catalogNext.setDisable(payload.pageIndex() + 1 >= payload.totalPages());
        }
        finally {
            updatingCatalog.set(false);
        }
    }

    private void closeCatalogSubscription() {
        if (catalogSubscription == null) return;
        try {
            catalogSubscription.close();
        }
        catch (Exception ignored) { }
        catalogSubscription = null;
    }

    static Rectangle2D calculateOverlayBounds(Rectangle2D screen, double preferredHeight) {
        Objects.requireNonNull(screen, "screen must not be null");
        if (screen.getWidth() <= 0 || screen.getHeight() <= 0
                || !Double.isFinite(preferredHeight) || preferredHeight < 0) {
            throw new IllegalArgumentException("Screen dimensions and preferred height must be valid");
        }
        double marginX = Math.min(SCREEN_MARGIN, screen.getWidth() / 4.0);
        double marginY = Math.min(SCREEN_MARGIN, screen.getHeight() / 4.0);
        double width = Math.min(OVERLAY_WIDTH, screen.getWidth() - 2 * marginX);
        double maximumHeight = Math.min(screen.getHeight() * 0.8, screen.getHeight() - 2 * marginY);
        double height = Math.min(maximumHeight, Math.max(MINIMUM_HEIGHT, preferredHeight));
        return new Rectangle2D(screen.getMaxX() - width - marginX,
                screen.getMaxY() - height - marginY, width, height);
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

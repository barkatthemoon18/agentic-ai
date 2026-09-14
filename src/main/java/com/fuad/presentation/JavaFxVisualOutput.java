package com.fuad.presentation;

import com.fuad.audio.AssistantAudioSnapshot;
import com.fuad.assistant.skills.os.ApplicationCatalogPayload;
import com.fuad.assistant.skills.os.CatalogNavigation;
import com.fuad.assistant.skills.os.CatalogSessionStore;
import com.fuad.assistant.skills.os.OpenApplicationsPayload;
import com.fuad.model.runtime.ComponentSnapshot;
import com.fuad.model.runtime.ComponentState;
import com.fuad.model.runtime.RuntimeComponent;
import com.fuad.model.runtime.RuntimeState;
import javafx.animation.*;
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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class JavaFxVisualOutput implements VisualOutput {
    private static final double OVERLAY_WIDTH = 560.0;
    private static final double MINIMUM_HEIGHT = 132.0;
    private static final double SCREEN_MARGIN = 24.0;
    private static final double HALO_PADDING = 24.0;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final AtomicBoolean closed =  new AtomicBoolean(false);
    private final JavaFxRuntime fxRuntime;
    private final boolean ownsRuntime;
    private final WindowsOverlayOwnerSupport ownerSupport;
    private final String ownerWindowTitle = "Ares Overlay Owner " + UUID.randomUUID();
    private Stage ownerStage;
    private Stage stage;
    private StackPane overlayRoot;
    private Label statusLabel;
    private Label messageLabel;
    private Label timeLabel;
    private ScrollPane messageScroll;
    private VBox catalogPane;
    private VBox infrastructurePane;
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
    private InfrastructureStatus pendingInfrastructureStatus;
    private DisplayMode displayMode = DisplayMode.NONE;

    public JavaFxVisualOutput() {
        this(new CatalogSessionStore(), new JavaFxRuntime(),
                WindowsOverlayOwnerSupport.platformDefault(), true);
    }

    public JavaFxVisualOutput(CatalogSessionStore catalogSessions) {
        this(catalogSessions, new JavaFxRuntime(),
                WindowsOverlayOwnerSupport.platformDefault(), true);
    }

    public JavaFxVisualOutput(CatalogSessionStore catalogSessions,
                              JavaFxRuntime fxRuntime) {
        this(catalogSessions, fxRuntime,
                WindowsOverlayOwnerSupport.platformDefault(), false);
    }

    JavaFxVisualOutput(CatalogSessionStore catalogSessions,
                       WindowsOverlayOwnerSupport ownerSupport) {
        this(catalogSessions, new JavaFxRuntime(), ownerSupport, true);
    }

    JavaFxVisualOutput(CatalogSessionStore catalogSessions,
                       JavaFxRuntime fxRuntime,
                       WindowsOverlayOwnerSupport ownerSupport,
                       boolean ownsRuntime) {
        this.catalogSessions = Objects.requireNonNull(catalogSessions, "catalogSessions must not be null");
        this.fxRuntime = Objects.requireNonNull(fxRuntime, "fxRuntime must not be null");
        this.ownerSupport = Objects.requireNonNull(ownerSupport, "ownerSupport must not be null");
        this.ownsRuntime = ownsRuntime;
        fxRuntime.runAndWait(this::createOverlay);
    }

    @Override
    public void show(VisualMessage visualMessage) {
        Objects.requireNonNull(visualMessage, "visualMessage must not be null");
        if (closed.get()) {
            throw new IllegalStateException("Visual output is already closed");
        }
        fxRuntime.runLater(() -> showInternal(visualMessage));
    }

    @Override
    public void showInfrastructureStatus(InfrastructureStatus status) {
        Objects.requireNonNull(status, "status must not be null");
        if (closed.get()) {
            return;
        }
        fxRuntime.runLater(() -> {
            pendingInfrastructureStatus = status;
            if (displayMode == DisplayMode.RESPONSE && stage != null && stage.isShowing()) {
                return;
            }
            showInfrastructureInternal(status);
        });
    }

    @Override
    public void hide() {
        if (closed.get()) {
            return;
        }
        fxRuntime.runLater(() -> {
            if (displayMode != DisplayMode.STATUS) {
                hideInternal();
            }
        });
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        closeCatalogSubscription();
        fxRuntime.runAndWait(() -> {
            stopAnimations();
            pendingInfrastructureStatus = null;
            if (stage != null) {
                stage.hide();
                stage.close();
            }
            if (ownerStage != null) {
                ownerStage.close();
            }
        });
        if (ownsRuntime) {
            fxRuntime.close();
        }
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

        infrastructurePane = new VBox(9.0);
        infrastructurePane.getStyleClass().add("ares-infrastructure");
        infrastructurePane.setVisible(false);
        infrastructurePane.setManaged(false);

        StackPane responseContent = new StackPane(messageScroll, catalogPane, infrastructurePane);
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

        ownerStage = new Stage(StageStyle.UTILITY);
        ownerStage.setTitle(ownerWindowTitle);
        ownerStage.setOpacity(0.0);
        ownerStage.setWidth(1.0);
        ownerStage.setHeight(1.0);
        ownerStage.setX(-32_000.0);
        ownerStage.setY(-32_000.0);
        ownerStage.setResizable(false);

        stage = new Stage(StageStyle.TRANSPARENT);
        stage.initOwner(ownerStage);
        stage.setTitle("Ares Response");
        stage.setAlwaysOnTop(true);
        stage.setResizable(false);
        stage.setScene(scene);

        long previousForegroundWindow = ownerSupport.captureForegroundWindow();
        try {
            ownerStage.show();
        }
        catch (RuntimeException e) {
            System.err.println("Unable to materialize JavaFX overlay owner: " + e.getMessage());
        }
        ownerSupport.configureAfterShow(ownerWindowTitle,
                ProcessHandle.current().pid(), previousForegroundWindow);

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
        displayMode = DisplayMode.RESPONSE;
        infrastructurePane.setVisible(false);
        infrastructurePane.setManaged(false);
        AssistantAudioSnapshot audioSnapshot = visualMessage.getAudioSnapshot();
        boolean catalog = visualMessage.getPayload() instanceof ApplicationCatalogPayload;
        boolean openApplications = visualMessage.getPayload() instanceof OpenApplicationsPayload;
        if (catalog) {
            showCatalog((ApplicationCatalogPayload) visualMessage.getPayload());
        }
        else if (openApplications) {
            showOpenApplications((OpenApplicationsPayload) visualMessage.getPayload());
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
        sizeOverlay(screenBounds, catalog || openApplications);
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
            if (!catalog && !openApplications) startDismissTimer(visualMessage.getText().length());
        });
        entrance.play();
    }

    private void hideInternal() {
        if (stage == null || !stage.isShowing()) {
            return;
        }
        stopAnimations();
        DisplayMode hiddenMode = displayMode;
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
            displayMode = DisplayMode.NONE;
            closeCatalogSubscription();
            overlayRoot.setOpacity(1.0);
            overlayRoot.setTranslateX(0.0);
            if (hiddenMode == DisplayMode.RESPONSE && pendingInfrastructureStatus != null) {
                showInfrastructureInternal(pendingInfrastructureStatus);
            }
        });
        exit.play();
    }

    private void showInfrastructureInternal(InfrastructureStatus status) {
        stopAnimations();
        closeCatalogSubscription();
        displayMode = DisplayMode.STATUS;
        messageScroll.setVisible(false);
        messageScroll.setManaged(false);
        catalogPane.setVisible(false);
        catalogPane.setManaged(false);
        infrastructurePane.setVisible(true);
        infrastructurePane.setManaged(true);
        infrastructurePane.getChildren().clear();

        if (status.snapshot().state() == RuntimeState.READY) {
            Label ready = new Label("phi-router y qwen-main están activos");
            ready.getStyleClass().add("ares-message");
            infrastructurePane.getChildren().add(ready);
            pendingInfrastructureStatus = null;
        }
        else {
            for (RuntimeComponent component : RuntimeComponent.values()) {
                ComponentSnapshot snapshot = status.snapshot().component(component);
                infrastructurePane.getChildren().add(createInfrastructureRow(snapshot, status));
            }
        }

        statusLabel.setText("●  RUNTIME " + status.snapshot().state());
        timeLabel.setText(LocalTime.now().format(TIME_FORMATTER));
        overlayRoot.setOpacity(0.0);
        overlayRoot.setTranslateX(20.0);
        Rectangle2D screenBounds = resolveTargetScreen().getVisualBounds();
        double preferredHeight = status.snapshot().state() == RuntimeState.READY
                ? MINIMUM_HEIGHT : 150.0 + RuntimeComponent.values().length * 48.0;
        Rectangle2D bounds = calculateOverlayBounds(screenBounds, preferredHeight);
        overlayRoot.setPrefSize(bounds.getWidth(), bounds.getHeight());
        stage.setWidth(bounds.getWidth());
        stage.setHeight(bounds.getHeight());
        stage.setX(bounds.getMinX());
        stage.setY(bounds.getMinY());
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
            if (status.snapshot().state() == RuntimeState.READY) {
                startDismissTimer(42);
            }
        });
        entrance.play();
    }

    private HBox createInfrastructureRow(ComponentSnapshot snapshot, InfrastructureStatus status) {
        Label name = new Label(componentName(snapshot.component()));
        name.getStyleClass().add("ares-infrastructure-name");
        Label state = new Label(snapshot.state().name());
        state.getStyleClass().add("ares-infrastructure-state");
        String detailText = snapshot.blockedBy() == null
                ? snapshot.detail() : snapshot.detail() + " (bloqueado)";
        if (snapshot.nextRetryAt() != null) {
            detailText += " · próximo intento "
                    + snapshot.nextRetryAt().atZone(java.time.ZoneId.systemDefault())
                    .toLocalTime().format(TIME_FORMATTER);
        }
        Label detail = new Label(detailText);
        detail.getStyleClass().add("ares-infrastructure-detail");
        detail.setWrapText(true);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(8.0, name, state, detail, spacer);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("ares-infrastructure-row");
        if (snapshot.state() == ComponentState.FAILED) {
            Button retry = new Button("Reintentar");
            retry.getStyleClass().add("ares-catalog-button");
            retry.setOnAction(event -> {
                retry.setDisable(true);
                status.retryAction().accept(snapshot.component());
            });
            row.getChildren().add(retry);
        }
        return row;
    }

    private static String componentName(RuntimeComponent component) {
        return switch (component) {
            case LMS_DAEMON -> "LMS daemon";
            case API_SERVER -> "API server";
            case PHI_ROUTER -> "phi-router";
            case QWEN_MAIN -> "qwen-main";
        };
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
        catalogSearch.setVisible(true);
        catalogSearch.setManaged(true);
        catalogPrevious.setVisible(true);
        catalogPrevious.setManaged(true);
        catalogNext.setVisible(true);
        catalogNext.setManaged(true);
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
        catalogSubscription = catalogSessions.observe(initial.sessionId(),
                payload -> fxRuntime.runLater(() -> renderCatalog(payload)));
    }

    private void showOpenApplications(OpenApplicationsPayload payload) {
        closeCatalogSubscription();
        messageScroll.setVisible(false);
        messageScroll.setManaged(false);
        catalogPane.setVisible(true);
        catalogPane.setManaged(true);
        catalogSearch.setVisible(false);
        catalogSearch.setManaged(false);
        catalogPrevious.setVisible(false);
        catalogPrevious.setManaged(false);
        catalogNext.setVisible(false);
        catalogNext.setManaged(false);
        catalogList.getItems().setAll(payload.items().stream()
                .map(item -> item.displayName()).toList());
        String summary = payload.items().size() + (payload.items().size() == 1
                ? " aplicación abierta" : " aplicaciones abiertas");
        if (payload.unverifiableCount() > 0) {
            summary += " · " + payload.unverifiableCount()
                    + (payload.unverifiableCount() == 1 ? " estado no verificable" : " estados no verificables");
        }
        catalogPageLabel.setText(summary);
        catalogPageLabel.setVisible(true);
        catalogPageLabel.setManaged(true);
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

    private enum DisplayMode {
        NONE,
        RESPONSE,
        STATUS
    }
}

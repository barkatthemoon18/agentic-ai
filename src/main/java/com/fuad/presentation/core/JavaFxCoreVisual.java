package com.fuad.presentation.core;

import com.fuad.enums.Capability;
import com.fuad.interaction.InteractionLifecycleListener;
import com.fuad.interaction.InteractionOutcome;
import com.fuad.pipeline.AssistantExecutionLifecycleListener;
import com.fuad.pipeline.VoiceInputController;
import com.fuad.pipeline.VoiceSignalSnapshot;
import com.fuad.presentation.JavaFxRuntime;
import com.fuad.media.control.MediaActionHandler;
import com.fuad.presentation.keyboard.AresKeyboardOverlay;
import com.fuad.presentation.media.MediaWorkspaceSnapshot;
import com.fuad.presentation.tools.MoreToolsHandler;
import com.fuad.presentation.tools.ToolsActionHandler;
import com.fuad.presentation.interaction.InteractionDisplayResolver;
import com.fuad.presentation.interaction.ResolvedInteractionDisplay;
import com.fuad.presentation.tools.ToolsWorkspaceConfig;
import com.fuad.terminal.PowershellSession;
import com.fuad.view.CoreDashboardView;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.scene.robot.Robot;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public class JavaFxCoreVisual implements CoreVisual, InteractionLifecycleListener, AssistantExecutionLifecycleListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(JavaFxCoreVisual.class);
    private final JavaFxRuntime javaFxRuntime;
    private final InteractionDisplayResolver interactionDisplayResolver;
    private final Supplier<VoiceSignalSnapshot> voiceSignalSupplier;
    private final VoiceInputController voiceInputController;
    private final ToolsActionHandler toolsActionHandler;
    private final MoreToolsHandler moreToolsHandler;
    private final ToolsWorkspaceConfig toolsConfig;
    private final Supplier<MediaWorkspaceSnapshot> mediaSnapshotSupplier;
    private final MediaActionHandler mediaActionHandler;
    private final PowershellSession terminalSession;
    private Stage stage;
    private CoreDashboardView dashboardView;
    private AresKeyboardOverlay keyboardOverlay;

    public JavaFxCoreVisual(JavaFxRuntime javaFxRuntime, InteractionDisplayResolver interactionDisplayResolver) {
        this(javaFxRuntime, interactionDisplayResolver, VoiceSignalSnapshot::silence, new VoiceInputController(), ToolsActionHandler.unavailable(),
                MoreToolsHandler.unavailable(), ToolsWorkspaceConfig.empty(), MediaWorkspaceSnapshot::unavailable, MediaActionHandler.unavailable(),
                new PowershellSession());
    }

    public JavaFxCoreVisual(JavaFxRuntime javaFxRuntime, InteractionDisplayResolver interactionDisplayResolver, Supplier<VoiceSignalSnapshot> voiceSignalSupplier,
                            VoiceInputController voiceInputController) {
        this(javaFxRuntime, interactionDisplayResolver, voiceSignalSupplier, voiceInputController,
                ToolsActionHandler.unavailable(), MoreToolsHandler.unavailable(), ToolsWorkspaceConfig.empty(),
                MediaWorkspaceSnapshot::unavailable, MediaActionHandler.unavailable(), new PowershellSession());
    }

    public JavaFxCoreVisual(JavaFxRuntime javaFxRuntime, InteractionDisplayResolver interactionDisplayResolver,
                            Supplier<VoiceSignalSnapshot> voiceSignalSupplier, VoiceInputController voiceInputController,
                            ToolsActionHandler toolsActionHandler, MoreToolsHandler moreToolsHandler, ToolsWorkspaceConfig toolsConfig,
                            Supplier<MediaWorkspaceSnapshot> mediaSnapshotSupplier, MediaActionHandler mediaActionHandler, PowershellSession terminalSession) {
        this.javaFxRuntime = Objects.requireNonNull(javaFxRuntime);
        this.interactionDisplayResolver =  Objects.requireNonNull(interactionDisplayResolver);
        this.voiceSignalSupplier = Objects.requireNonNull(voiceSignalSupplier);
        this.voiceInputController = Objects.requireNonNull(voiceInputController);
        this.toolsActionHandler = Objects.requireNonNull(toolsActionHandler);
        this.moreToolsHandler = Objects.requireNonNull(moreToolsHandler);
        this.toolsConfig = Objects.requireNonNull(toolsConfig);
        this.mediaSnapshotSupplier = Objects.requireNonNull(mediaSnapshotSupplier);
        this.mediaActionHandler = Objects.requireNonNull(mediaActionHandler);
        this.terminalSession = Objects.requireNonNull(terminalSession);

        javaFxRuntime.runAndWait(() -> {
            createStage();
            try {
                keyboardOverlay = new AresKeyboardOverlay(javaFxRuntime, voiceInputController, this::focusCommandDeck);
            }
            catch (RuntimeException | LinkageError e) {
                LOGGER.warn("Keyboard overlay unavailable; ARES continues normally", e);
            }
        });
    }

    @Override
    public void show() {
        javaFxRuntime.runLater(stage::show);
    }

    @Override
    public void update(CoreVisualSnapshot visualSnapshot) {
        javaFxRuntime.runLater(() -> dashboardView.update(visualSnapshot));
    }

    @Override
    public void close() {
        javaFxRuntime.runAndWait(() -> {
            if (keyboardOverlay != null) {
                keyboardOverlay.close();
                keyboardOverlay = null;
            }
            if (dashboardView != null) {
                dashboardView.dispose();
            }
            if (stage != null) {
                stage.close();
            }
        });
    }

    @Override
    public void onVisible(UUID sessionId) {
        javaFxRuntime.runLater(() -> dashboardView.interactionVisible(sessionId));
    }

    @Override
    public void onCompleted(UUID sessionId, InteractionOutcome outcome) {
        javaFxRuntime.runLater(() -> dashboardView.interactionCompleted(sessionId));
    }

    @Override
    public void onExecutionCompleted(UUID executionId, Capability capability) {
        javaFxRuntime.runLater(() -> dashboardView.executionCompleted(executionId));
    }

    @Override
    public void onExecutionStarted(UUID executionId, Capability capability) {
        javaFxRuntime.runLater(() -> dashboardView.executionStarted(executionId));
    }

    private void focusCommandDeck() {
        stage.setIconified(false);
        if (!stage.isShowing()) {
            stage.show();
        }
        if (keyboardOverlay != null) {
            keyboardOverlay.yieldToDeck(new Rectangle2D(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight()));
        }
        stage.toFront();
        stage.requestFocus();
        dashboardView.requestFocus();
        var bounds = dashboardView.localToScreen(dashboardView.getBoundsInLocal());
        if (bounds != null) {
            try {
                new Robot().mouseMove(bounds.getCenterX(), bounds.getCenterY());
            }
            catch (RuntimeException | LinkageError e) {
                LOGGER.warn("Command Deck focused, but unable to move pointer to its display", e);
            }
        }
    }

    private void createStage() {
        ResolvedInteractionDisplay interactionDisplay = interactionDisplayResolver.resolve().orElseThrow(() ->
                new IllegalStateException("Core display unavailable"));
        Rectangle2D bounds = interactionDisplay.screen().getVisualBounds();
        dashboardView = new CoreDashboardView(voiceSignalSupplier, voiceInputController, toolsActionHandler, moreToolsHandler,
                toolsConfig, mediaSnapshotSupplier, mediaActionHandler, terminalSession);
        Scene scene = new Scene(dashboardView, bounds.getWidth(), bounds.getHeight());
        scene.setFill(Color.rgb(4, 13, 22));
        URL css = JavaFxCoreVisual.class.getResource("/ui/core-visual.css");
        if (css == null) {
            throw new IllegalStateException("Missing /ui/core-visual.css");
        }
        scene.getStylesheets().add(css.toExternalForm());
        stage = new Stage(StageStyle.UNDECORATED);
        stage.setTitle("Ares Core Visual");
        stage.setX(bounds.getMinX());
        stage.setY(bounds.getMinY());
        stage.setWidth(bounds.getWidth());
        stage.setHeight(bounds.getHeight());
        stage.setResizable(false);
        stage.setAlwaysOnTop(false);
        stage.setScene(scene);
    }
}

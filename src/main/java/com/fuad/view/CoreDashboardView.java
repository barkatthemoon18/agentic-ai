package com.fuad.view;

import com.fuad.pipeline.VoiceInputController;
import com.fuad.pipeline.VoiceSignalSnapshot;
import com.fuad.presentation.core.AssistantVisualState;
import com.fuad.presentation.core.AssistantVisualStateCoordinator;
import com.fuad.presentation.core.CoreVisualSnapshot;
import com.fuad.presentation.core.WorkspaceType;
import com.fuad.view.workspace.web.ResearchTtsState;
import com.fuad.view.workspace.web.ResearchWorkspaceSnapshot;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public final class CoreDashboardView extends StackPane {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy");
    public static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final PseudoClass STATE_IDLE = PseudoClass.getPseudoClass("idle");
    public static final PseudoClass STATE_LISTENING = PseudoClass.getPseudoClass("listening");
    public static final PseudoClass STATE_PROCESSING = PseudoClass.getPseudoClass("processing");
    public static final PseudoClass STATE_INTERACTING = PseudoClass.getPseudoClass("interacting");
    public static final PseudoClass STATE_EXECUTING = PseudoClass.getPseudoClass("executing");
    public static final PseudoClass STATE_SPEAKING = PseudoClass.getPseudoClass("speaking");
    public static final PseudoClass STATE_DEGRADED = PseudoClass.getPseudoClass("degraded");
    private final TelemetryPanel telemetryPanel = new TelemetryPanel();
    private final AresCoreView coreView;
    private final AresWorkspaceView aresWorkspaceView;
    private final VoiceInputController voiceInputController;
    private final RuntimePanel runtimePanel = new RuntimePanel();
    private final Label assistantStateLabel = new Label("● IDLE");
    private final Label runtimeSummaryLabel = new Label("MOCK TELEMETRY");
    private final Label dateLabel = new Label();
    private final Label clockLabel = new Label();
    private final Timeline clock;
    private final AssistantVisualStateCoordinator stateCoordinator;
    private CoreVisualSnapshot latestSnapshot;
    private UUID activeInteractionSessionId;

    public CoreDashboardView() {
        this (VoiceSignalSnapshot::silence, null);
    }

    public CoreDashboardView(Supplier<VoiceSignalSnapshot> voiceSignalSupplier, VoiceInputController voiceInputController) {
        this.voiceInputController = Objects.requireNonNull(voiceInputController);

        coreView = new AresCoreView(Objects.requireNonNull(voiceSignalSupplier));
        stateCoordinator = new AssistantVisualStateCoordinator(this::handleEffectiveVisualState);
        aresWorkspaceView = new AresWorkspaceView(this::handleResearchLifecycle);

        HudBackground hudBackground = new HudBackground();

        VBox left = new VBox(18.0, telemetryPanel, runtimePanel);
        left.setPrefWidth(390.0);
        left.setMinHeight(0.0);
        left.setMaxHeight(Double.MAX_VALUE);
        left.setFillWidth(true);

        telemetryPanel.setPrefWidth(390.0);
        telemetryPanel.setMaxWidth(Double.MAX_VALUE);

        runtimePanel.setPrefWidth(390.0);
        runtimePanel.setMinHeight(0.0);
        runtimePanel.setMaxHeight(Double.MAX_VALUE);

        VBox.setVgrow(runtimePanel, Priority.ALWAYS);

        coreView.setPrefWidth(760.0);
        coreView.setMinHeight(0.0);
        coreView.setMaxHeight(Double.MAX_VALUE);

        aresWorkspaceView.setPrefWidth(666.0);
        aresWorkspaceView.setMinHeight(0.0);
        aresWorkspaceView.setMaxHeight(Double.MAX_VALUE);

        HBox body = new HBox(24.0, left, coreView, aresWorkspaceView);
        body.setAlignment(Pos.CENTER);
        body.setFillHeight(true);
        body.setMinHeight(0.0);
        body.setMaxHeight(Double.MAX_VALUE);

        VBox.setVgrow(body, Priority.ALWAYS);

        VBox layout = new VBox(18.0, createHeader(), body, createFooter());
        layout.setPadding(new Insets(28.0));
        getChildren().addAll(hudBackground, layout);
        getStyleClass().add("ares-core-root");
        clock = new Timeline(new KeyFrame(Duration.ZERO, event -> updateClock()), new KeyFrame(Duration.seconds(1)));
        clock.setCycleCount(Animation.INDEFINITE);
        clock.play();
    }

    public void update(CoreVisualSnapshot visualSnapshot) {
        latestSnapshot = visualSnapshot;
        stateCoordinator.updateBaseState(visualSnapshot.assistantVisualState());

        renderSnapshot(visualSnapshot.withAssistantVisualState(stateCoordinator.getEffectiveState()));
    }

    public void interactionVisible(UUID sessionId) {
        activeInteractionSessionId = Objects.requireNonNull(sessionId);
        stateCoordinator.setOverride(AssistantVisualStateCoordinator.Source.INTERACTION, AssistantVisualState.INTERACTING);
    }

    public void interactionCompleted(UUID sessionId) {
        if (!Objects.equals(activeInteractionSessionId, sessionId)) {
            return;
        }
        activeInteractionSessionId = null;
        stateCoordinator.clearOverride(AssistantVisualStateCoordinator.Source.INTERACTION);
    }

    public void showWorkspace(WorkspaceType workspaceType) {
        aresWorkspaceView.showWorkspace(workspaceType);
    }

    public void dispose() {
        clock.stop();
        coreView.dispose();
    }

    private Region createHeader() {
        Label title = new Label("ARES // SYSTEM");
        Label subtitle = new Label("VISUAL CORE");
        Label clockSeparator = new Label("//");

        title.getStyleClass().add("core-header-title");
        subtitle.getStyleClass().add("core-header-subtitle");

        VBox identity = new VBox(2.0, title, subtitle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        assistantStateLabel.getStyleClass().add("core-header-status");
        dateLabel.getStyleClass().add("core-header-date");
        clockLabel.getStyleClass().add("core-header-clock");
        clockSeparator.getStyleClass().add("core-header-clock-separator");

        HBox clockGroup = new HBox(11.0, dateLabel, clockSeparator, clockLabel);
        clockGroup.setAlignment(Pos.CENTER_LEFT);

        HBox headerContent = new HBox(22.0, identity, spacer, assistantStateLabel, clockGroup);
        headerContent.setAlignment(Pos.CENTER_LEFT);

        Region separator = new Region();
        separator.getStyleClass().add("core-header-separator");
        separator.setMinHeight(1.0);
        separator.setPrefHeight(1.0);
        separator.setMaxHeight(1.0);

        VBox header = new VBox(10.0, headerContent, separator);
        header.setMinHeight(72.0);
        header.setPrefHeight(72.0);
        header.setMaxHeight(72.0);
        return header;
    }

    private Region createFooter() {
        Label systemStatus = new Label("● ARES // READY");
        Label modality = new Label("● VOICE + TOUCH");
        ToggleButton muteButton = new ToggleButton("MUTE");

        systemStatus.getStyleClass().add("core-footer-primary");
        runtimeSummaryLabel.getStyleClass().add("core-footer-secondary");
        modality.getStyleClass().add("core-footer-status");
        muteButton.getStyleClass().add("core-footer-mute");
        muteButton.setSelected(voiceInputController.isMuted());

        updateVoiceInputControls(muteButton, modality);
        muteButton.selectedProperty().addListener((observable, oldValue, newValue) -> {
            voiceInputController.setMuted(newValue);
            updateVoiceInputControls(muteButton, modality);
        });

        HBox left = new HBox(systemStatus);
        HBox center = new HBox(runtimeSummaryLabel);
        HBox right = new HBox(10.0, muteButton, modality);
        left.setAlignment(Pos.CENTER_LEFT);
        center.setAlignment(Pos.CENTER);
        right.setAlignment(Pos.CENTER_RIGHT);

        GridPane footer = new GridPane();

        ColumnConstraints columnLeft = new ColumnConstraints();
        ColumnConstraints columnCenter = new ColumnConstraints();
        ColumnConstraints columnRight = new ColumnConstraints();

        columnLeft.setPercentWidth(33.333);
        columnCenter.setPercentWidth(33.333);
        columnRight.setPercentWidth(33.333);

        footer.getColumnConstraints().addAll(columnLeft, columnCenter, columnRight);
        footer.add(left, 0, 0);
        footer.add(center, 1, 0);
        footer.add(right, 2, 0);

        left.setMaxWidth(Double.MAX_VALUE);
        center.setMaxWidth(Double.MAX_VALUE);
        right.setMaxWidth(Double.MAX_VALUE);

        footer.setMinHeight(52.0);
        footer.setPrefHeight(52.0);
        footer.setMaxHeight(52.0);

        footer.getStyleClass().add("core-footer");
        return footer;
    }

    private void updateVoiceInputControls(ToggleButton muteButton, Label modality) {
        boolean muted = muteButton.isSelected();

        muteButton.setText(muted ? "MUTE // ON" : "MUTE");
        modality.setText(muted ? "● TOUCH ONLY" : "● VOICE + TOUCH");
    }

    private void updateClock() {
        LocalDateTime now = LocalDateTime.now();

        dateLabel.setText(now.format(DATE_FORMAT).toUpperCase());
        clockLabel.setText(now.format(TIME_FORMAT));
    }

    private void renderSnapshot(CoreVisualSnapshot visualSnapshot) {
        telemetryPanel.update(visualSnapshot);
        runtimePanel.update(visualSnapshot);
        coreView.update(visualSnapshot);

        updateHeaderState(visualSnapshot.assistantVisualState());
        runtimeSummaryLabel.setText("PHI // " + visualSnapshot.runtimeSnapshot().phiState() + "     QWEN // " +
                visualSnapshot.runtimeSnapshot().qwenState());
    }

    private void handleResearchLifecycle(ResearchWorkspaceSnapshot snapshot) {
        switch (snapshot.state()) {
            case RESEARCHING, PARTIAL -> stateCoordinator.setOverride(AssistantVisualStateCoordinator.Source.RESEARCH,
                    AssistantVisualState.PROCESSING);
            case COMPLETE -> {
                if (snapshot.ttsState() == ResearchTtsState.SPEAKING) {
                    stateCoordinator.clearOverride(AssistantVisualStateCoordinator.Source.RESEARCH);
                    stateCoordinator.setOverride(AssistantVisualStateCoordinator.Source.TTS, AssistantVisualState.SPEAKING);
                }
                else if (snapshot.ttsState() == ResearchTtsState.DELIVERED) {
                    stateCoordinator.clearOverride(AssistantVisualStateCoordinator.Source.RESEARCH);
                    stateCoordinator.clearOverride(AssistantVisualStateCoordinator.Source.TTS);
                }
            }
            case FAILED -> stateCoordinator.setOverride(AssistantVisualStateCoordinator.Source.RESEARCH, AssistantVisualState.DEGRADED);
            case IDLE -> stateCoordinator.clearOverride(AssistantVisualStateCoordinator.Source.RESEARCH);
        }
    }

    private void handleEffectiveVisualState(AssistantVisualState state) {
        if (latestSnapshot == null) {
            return;
        }
        renderSnapshot(latestSnapshot.withAssistantVisualState(state));
    }

    private void updateHeaderState(AssistantVisualState state) {
        clearHeaderState();

        assistantStateLabel.setText("● STATE // " + state.name());
        PseudoClass pseudoClass = switch (state) {
            case IDLE -> STATE_IDLE;
            case LISTENING -> STATE_LISTENING;
            case PROCESSING -> STATE_PROCESSING;
            case INTERACTING -> STATE_INTERACTING;
            case EXECUTING -> STATE_EXECUTING;
            case SPEAKING -> STATE_SPEAKING;
            case DEGRADED -> STATE_DEGRADED;
        };
        assistantStateLabel.pseudoClassStateChanged(pseudoClass, true);
    }

    private void clearHeaderState() {
        assistantStateLabel.pseudoClassStateChanged(STATE_IDLE, false);
        assistantStateLabel.pseudoClassStateChanged(STATE_LISTENING, false);
        assistantStateLabel.pseudoClassStateChanged(STATE_PROCESSING, false);
        assistantStateLabel.pseudoClassStateChanged(STATE_INTERACTING, false);
        assistantStateLabel.pseudoClassStateChanged(STATE_EXECUTING, false);
        assistantStateLabel.pseudoClassStateChanged(STATE_SPEAKING, false);
        assistantStateLabel.pseudoClassStateChanged(STATE_DEGRADED, false);
    }
}

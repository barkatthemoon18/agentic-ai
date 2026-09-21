package com.fuad.view;

import com.fuad.presentation.core.AssistantVisualState;
import com.fuad.presentation.core.AssistantVisualStateCoordinator;
import com.fuad.presentation.core.CoreVisualSnapshot;
import com.fuad.presentation.core.WorkspaceType;
import com.fuad.view.workspace.web.ResearchTtsState;
import com.fuad.view.workspace.web.ResearchWorkspaceSnapshot;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class CoreDashboardView extends StackPane {
    private static final DateTimeFormatter CLOCK_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy  HH:mm:ss");
    private final TelemetryPanel telemetryPanel = new TelemetryPanel();
    private final AresCoreView coreView =  new AresCoreView();
    private final AresWorkspaceView aresWorkspaceView;
    private final RuntimePanel runtimePanel = new RuntimePanel();
    private final Label assistantStateLabel = new Label("● IDLE");
    private final Label runtimeSummaryLabel = new Label("MOCK TELEMETRY");
    private final Label clockLabel = new Label();
    private final Timeline clock;
    private final AssistantVisualStateCoordinator stateCoordinator;
    private CoreVisualSnapshot latestSnapshot;

    public CoreDashboardView() {
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

    public void showWorkspace(WorkspaceType workspaceType) {
        aresWorkspaceView.showWorkspace(workspaceType);
    }

    public void dispose() {
        clock.stop();
        coreView.dispose();
    }

    private Region createHeader() {
        Label title = new Label("ARES // SYSTEM");
        title.getStyleClass().add("core-header-title");
        Label subtitle = new Label("VISUAL CORE");
        subtitle.getStyleClass().add("core-header-subtitle");
        VBox identity = new VBox(2.0, title, subtitle);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        assistantStateLabel.getStyleClass().add("core-header-status");
        clockLabel.getStyleClass().add("core-header-clock");
        HBox headerContent = new HBox(22.0, identity, spacer, assistantStateLabel, clockLabel);
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
        Label left = new Label("ARES READY");

        left.getStyleClass().add("core-footer-primary");
        runtimeSummaryLabel.getStyleClass().add("core-footer-secondary");
        Label modality = new Label("● VOZ + TÁCTIL");
        modality.getStyleClass().add("core-footer-status");
        Region leftSpacer = new Region();
        Region rightSpacer = new Region();
        HBox.setHgrow(leftSpacer, Priority.ALWAYS);
        HBox.setHgrow(rightSpacer, Priority.ALWAYS);
        HBox footer = new HBox(18.0, left, leftSpacer, runtimeSummaryLabel, rightSpacer, modality);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setMinHeight(52.0);
        footer.setPrefHeight(52.0);
        footer.setMaxHeight(52.0);
        footer.getStyleClass().add("core-footer");
        return footer;
    }

    private void updateClock() {
        clockLabel.setText(LocalDateTime.now().format(CLOCK_FORMAT).toUpperCase());
    }

    private void renderSnapshot(CoreVisualSnapshot visualSnapshot) {
        telemetryPanel.update(visualSnapshot);
        runtimePanel.update(visualSnapshot);
        coreView.update(visualSnapshot);

        assistantStateLabel.setText("● " + visualSnapshot.assistantVisualState().name());
        runtimeSummaryLabel.setText("PHI " + visualSnapshot.runtimeSnapshot().phiState() + "  //  QWEN " +
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
}

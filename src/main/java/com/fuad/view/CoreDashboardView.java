package com.fuad.view;

import com.fuad.presentation.core.CoreVisualSnapshot;
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
    private final AresWorkspaceView aresWorkspaceView = new AresWorkspaceView();
    private final RuntimePanel runtimePanel = new RuntimePanel();
    private final Label assistantStateLabel = new Label("● IDLE");
    private final Label runtimeSummaryLabel = new Label("MOCK TELEMETRY");
    private final Label clockLabel = new Label();
    private final Timeline clock;

    public CoreDashboardView() {
        HudBackground hudBackground = new HudBackground();
        VBox left = new VBox(18.0, telemetryPanel, runtimePanel);
        left.setPrefWidth(390.0);
        telemetryPanel.setPrefWidth(390.0);
        runtimePanel.setPrefWidth(390.0);
        coreView.setPrefWidth(760.0);
        aresWorkspaceView.setPrefWidth(666.0);
        HBox body = new HBox(24.0, left, coreView, aresWorkspaceView);
        body.setAlignment(Pos.CENTER);
        VBox layout = new VBox(18.0, createHeader(), body, createFooter());
        layout.setPadding(new Insets(28.0));
        getChildren().addAll(hudBackground, layout);
        getStyleClass().add("ares-core-root");
        clock = new Timeline(new KeyFrame(Duration.ZERO, event -> updateClock()), new KeyFrame(Duration.seconds(1)));
        clock.setCycleCount(Timeline.INDEFINITE);
        clock.play();
    }

    public void update(CoreVisualSnapshot visualSnapshot) {
        telemetryPanel.update(visualSnapshot);
        runtimePanel.update(visualSnapshot);
        coreView.update(visualSnapshot);

        assistantStateLabel.setText("● " + visualSnapshot.assistantVisualState().name());
        runtimeSummaryLabel.setText("PHI " + visualSnapshot.runtimeSnapshot().phiState() + "  //  QWEN " +
                visualSnapshot.runtimeSnapshot().qwenState());
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
        header.setPrefHeight(72.0);
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
        footer.setPrefHeight(52.0);
        footer.getStyleClass().add("core-footer");
        return footer;
    }

    private void updateClock() {
        clockLabel.setText(LocalDateTime.now().format(CLOCK_FORMAT).toUpperCase());
    }
}

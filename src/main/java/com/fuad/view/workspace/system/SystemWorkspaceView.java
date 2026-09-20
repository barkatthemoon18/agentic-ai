package com.fuad.view.workspace.system;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.*;

import java.time.Duration;
import java.util.List;

public class SystemWorkspaceView extends VBox {
    private static final double VOLUME_BAR_WIDTH = 165.0;
    private final Label outputValue = new Label();
    private final Label inputValue = new Label();
    private final Label volumeValue = new Label();
    private final Label networkStatus = new Label();
    private final Label adapterValue = new Label();
    private final Label ipValue = new Label();
    private final Label linkSpeedValue = new Label();
    private final Label downloadValue = new Label();
    private final Label uploadValue = new Label();
    private final Label latencyValue = new Label();
    private final Label powerPlanValue = new Label();
    private final Label powerSourceValue = new Label();
    private final Label hostValue = new Label();
    private final Label sessionValue = new Label();
    private final Label uptimeValue = new Label();
    private final Label audioStatus = new Label();
    private final Region volumeFill = new Region();
    private final VBox displayList = new VBox(7.0);

    public SystemWorkspaceView() {
        setSpacing(12.0);

        Label title = new Label("SYSTEM // CONTROL");
        Label provider = new Label("LOCAL MACHINE // MOCK");

        title.getStyleClass().add("workspace-view-title");
        provider.getStyleClass().add("system-provider");

        VBox identity = new VBox(2.0, title, provider);
        GridPane modules = createModules();
        VBox.setVgrow(modules, Priority.ALWAYS);
        getChildren().addAll(identity, modules);
        update(mockSnapshot());
    }

    public void update(SystemWorkspaceSnapshot snapshot) {
        updateAudio(snapshot.audioSnapshot());
        updateNetwork(snapshot.networkSnapshot());
        updateDisplays(snapshot.displays());
        updatePower(snapshot.powerSnapshot());
    }

    private void updateAudio(SystemAudioSnapshot snapshot) {
        audioStatus.setText(snapshot.muted() ? "● MUTED" : "● ACTIVE");
        outputValue.setText(snapshot.outputDevice());
        inputValue.setText(snapshot.inputDevice());
        volumeValue.setText("%.0f %%".formatted(snapshot.volume() * 100.0));
        double normalized = Math.clamp(snapshot.volume(), 0.0, 1.0);
        double width = VOLUME_BAR_WIDTH * normalized;
        volumeFill.setMinWidth(width);
        volumeFill.setPrefWidth(width);
        volumeFill.setMaxWidth(width);
    }

    private void updateNetwork(SystemNetworkSnapshot snapshot) {
        networkStatus.setText("● " + snapshot.status().toUpperCase());
        adapterValue.setText(snapshot.adapter());
        ipValue.setText(snapshot.ipAddress());
        linkSpeedValue.setText("%.1f Gbps".formatted(snapshot.linkSpeedMbps() / 1000.0));
        downloadValue.setText("%.1f Mbps".formatted(snapshot.downloadMbps()));
        uploadValue.setText("%.1f Mbps".formatted(snapshot.uploadMbps()));
        latencyValue.setText("%.0f ms".formatted(snapshot.latencyMs()));
    }

    private void updateDisplays(List<SystemDisplaySnapshot> snapshots) {
        displayList.getChildren().clear();

        for (SystemDisplaySnapshot snapshot : snapshots) {
            displayList.getChildren().add(createDisplayRow(snapshot));
        }
    }

    private void updatePower(SystemPowerSnapshot snapshot) {
        powerPlanValue.setText(snapshot.plan());
        powerSourceValue.setText(snapshot.source());
        hostValue.setText(snapshot.host());
        sessionValue.setText(snapshot.sessionState());
        uptimeValue.setText(formatDuration(snapshot.uptime()));
    }

    private GridPane createModules() {
        GridPane gridPane = new GridPane();

        gridPane.setHgap(12.0);
        gridPane.setVgap(12.0);

        ColumnConstraints left = new ColumnConstraints();
        left.setPercentWidth(50.0);
        left.setHgrow(Priority.ALWAYS);

        ColumnConstraints right = new ColumnConstraints();
        right.setPercentWidth(50.0);
        right.setHgrow(Priority.ALWAYS);

        RowConstraints top = new RowConstraints();
        top.setPercentHeight(50.0);
        top.setVgrow(Priority.ALWAYS);

        RowConstraints bottom = new RowConstraints();
        bottom.setPercentHeight(50.0);
        bottom.setVgrow(Priority.ALWAYS);

        gridPane.getColumnConstraints().addAll(left, right);
        gridPane.getRowConstraints().addAll(top, bottom);

        VBox audio = createAudioPanel();
        VBox network = createNetworkPanel();
        VBox displays = createDisplaysPanel();
        VBox power = createPowerPanel();

        gridPane.add(audio, 0, 0);
        gridPane.add(network, 1, 0);
        gridPane.add(displays, 0, 1);
        gridPane.add(power, 1, 1);

        makeFill(audio);
        makeFill(network);
        makeFill(displays);
        makeFill(power);

        return gridPane;
    }

    private VBox createAudioPanel() {
        Label title = sectionTitle("AUDIO // DEVICES");

        audioStatus.getStyleClass().add("system-status");

        addValueStyle(outputValue);
        addValueStyle(inputValue);
        addValueStyle(volumeValue);

        GridPane properties = new GridPane();

        properties.setHgap(14.0);
        properties.setVgap(8.0);

        addProperty(properties, 0, "OUTPUT", outputValue);
        addProperty(properties, 1, "INPUT", inputValue);

        Label volumeKey = propertyKey("VOLUME");

        StackPane volumeBar = createVolumeBar();

        HBox volumeRow = new HBox(10.0, volumeKey, volumeValue, volumeBar);
        volumeRow.setAlignment(Pos.CENTER_LEFT);

        VBox panel = new VBox(13.0, title, audioStatus, properties, volumeRow);
        configureCard(panel);
        return panel;
    }

    private StackPane createVolumeBar() {
        Region track = new Region();

        track.getStyleClass().add("system-level-track");
        track.setMinSize(VOLUME_BAR_WIDTH, 5.0);
        track.setPrefSize(VOLUME_BAR_WIDTH, 5.0);
        track.setMaxSize(VOLUME_BAR_WIDTH, 5.0);

        volumeFill.getStyleClass().add("system-level-fill");
        volumeFill.setMinHeight(5.0);
        volumeFill.setPrefHeight(5.0);
        volumeFill.setMaxHeight(5.0);

        StackPane bar = new StackPane(track, volumeFill);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setMinSize(VOLUME_BAR_WIDTH, 5.0);
        bar.setPrefSize(VOLUME_BAR_WIDTH, 5.0);
        bar.setMaxSize(VOLUME_BAR_WIDTH, 5.0);
        return bar;
    }

    private VBox createNetworkPanel() {
        Label title = sectionTitle("NETWORK // LINK");

        networkStatus.getStyleClass().add("system-status");

        addValueStyle(adapterValue);
        addValueStyle(ipValue);
        addValueStyle(linkSpeedValue);

        GridPane properties = new GridPane();
        properties.setHgap(14.0);
        properties.setVgap(8.0);

        addProperty(properties, 0, "ADAPTER", adapterValue);
        addProperty(properties, 1, "IP", ipValue);
        addProperty(properties, 2, "LINK", linkSpeedValue);

        downloadValue.getStyleClass().add("system-network-rate");
        uploadValue.getStyleClass().add("system-network-rate");
        latencyValue.getStyleClass().add("system-network-rate");

        HBox traffic = new HBox(22.0, createTrafficValue("DOWN", downloadValue), createTrafficValue("UP", uploadValue), createTrafficValue("LATENCY", latencyValue));

        VBox panel = new VBox(13.0, title, networkStatus, properties, traffic);
        configureCard(panel);
        return panel;
    }

    private VBox createTrafficValue(String key, Label value) {
        Label label = propertyKey(key);

        return new VBox(3.0, label, value);
    }

    private VBox createDisplaysPanel() {
        Label title = sectionTitle("DISPLAYS // TOPOLOGY");

        VBox panel = new VBox(13.0, title, displayList);
        configureCard(panel);
        return panel;
    }

    private HBox createDisplayRow(SystemDisplaySnapshot display) {
        String role;
        String mode = "%d×%d @ %.0f Hz".formatted(display.width(), display.height(), display.refreshRate());
        Label id = new Label(display.id());

        id.getStyleClass().add("system-display-id");

        Label resolution = new Label(mode);
        resolution.getStyleClass().add("system-display-mode");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);


        if (display.interactionDisplay()) {
            role = "ARES";
        }
        else {
            role = display.primary() ? "PRIMARY" : "EXT";
        }
        Label roleLabel = new Label(role);
        roleLabel.getStyleClass().add(display.interactionDisplay() ? "system-display-role-active" : "system-display-role");

        HBox row = new HBox(10.0, id, resolution, spacer, roleLabel);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("system-display-row");
        return row;
    }

    private VBox createPowerPanel() {
        Label title = sectionTitle("POWER // SYSTEM");
        Label powerCaption = subsectionTitle("POWER");
        Label sessionCaption = subsectionTitle("SESSION");

        addValueStyle(powerPlanValue);
        addValueStyle(powerSourceValue);
        addValueStyle(hostValue);
        addValueStyle(sessionValue);
        addValueStyle(uptimeValue);

        GridPane power = new GridPane();
        power.setHgap(14.0);
        power.setVgap(8.0);

        addProperty(power, 0, "PLAN", powerPlanValue);
        addProperty(power, 1, "SOURCE", powerSourceValue);

        GridPane session = new GridPane();
        session.setHgap(14.0);
        session.setVgap(8.0);

        addProperty(session, 0, "HOST", hostValue);
        addProperty(session, 1, "STATE", sessionValue);
        addProperty(session, 2, "UPTIME", uptimeValue);

        VBox panel = new VBox(13.0, title, powerCaption, power, sessionCaption, session);
        configureCard(panel);
        return panel;
    }

    private static SystemWorkspaceSnapshot mockSnapshot() {
        return new SystemWorkspaceSnapshot(new SystemAudioSnapshot("FOCUSRITE USB", "FOCUSRITE USB",
                        0.62,
                        false),
                new SystemNetworkSnapshot("CONNECTED", "ETHERNET", "192.168.1.42", 1000, 14.5,
                        3.2, 5),
                List.of(new SystemDisplaySnapshot("01", 2560, 1440, 85.0, true, false),
                    new SystemDisplaySnapshot("02", 1920, 1080, 60.0, false, true),
                    new SystemDisplaySnapshot("03", 2560, 1440, 75.0, false, false)),
                    new SystemPowerSnapshot("BALANCED", "AC POWER", "ARES-DESKTOP", "ACTIVE",
                            Duration.ofHours(3).plusMinutes(42).plusSeconds(18)));
    }

    private static Label subsectionTitle(String subsection) {
        Label label = new Label(subsection);

        label.getStyleClass().add("system-subsection-title");
        return label;
    }

    private static void makeFill(Region region) {
        region.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        GridPane.setHgrow(region, Priority.ALWAYS);
        GridPane.setVgrow(region, Priority.ALWAYS);
        GridPane.setFillWidth(region, true);
        GridPane.setFillHeight(region, true);
    }

    private static String formatDuration(Duration duration) {
        long seconds = duration.toSeconds();
        long minutes = (seconds % 3600) / 60;
        long hours = seconds / 3600;
        long remainingSeconds = seconds % 60;

        return "%02d:%02d:%02d".formatted(hours, minutes, remainingSeconds);
    }

    private static void configureCard(VBox panel) {
        panel.setPadding(new Insets(15.0));
        panel.getStyleClass().add("system-card");
    }

    private static Label sectionTitle(String title) {
        Label label = new Label(title);
        label.getStyleClass().add("system-section-title");
        return label;
    }

    private static Label propertyKey(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("system-property-key");
        return label;
    }

    private static void addValueStyle(Label value) {
        value.getStyleClass().add("system-property-value");
    }

    private static void addProperty(GridPane gridPane, int row, String key, Label value) {
        gridPane.add(propertyKey(key), 0, row);
        gridPane.add(value, 1, row);
    }
}

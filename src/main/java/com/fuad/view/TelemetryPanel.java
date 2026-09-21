package com.fuad.view;

import com.fuad.presentation.core.CoreVisualSnapshot;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class TelemetryPanel extends VBox {
    private final HudMetric cpu = new HudMetric("CPU");
    private final HudMetric ram = new HudMetric("RAM");
    private final HudMetric gpu = new HudMetric("GPU");
    private final HudMetric vram = new HudMetric("VRAM");
    private final HudMetric gpuTemp = new HudMetric("GPU T°");
    private final Label ip = new Label();
    private final Label download = new Label();
    private final Label upload = new Label();

    public TelemetryPanel() {
        setSpacing(14.0);
        setPadding(new Insets(18.0));

        getStyleClass().addAll("core-panel", "telemetry-panel");

        Label title = new Label("SYSTEM // TELEMETRY");
        title.getStyleClass().addAll("core-hud-title");

        Label live = new Label("LIVE // 1s");
        live.getStyleClass().addAll("telemetry-live");

        Label systemTitle = sectionTitle("SYSTEM LOAD");

        Label gpuTitle = sectionTitle("GPU LOAD");

        Label networkTitle = sectionTitle("NETWORK ACTIVITY");

        ip.getStyleClass().addAll("telemetry-network-ip");
        download.getStyleClass().addAll("telemetry-network-rate");
        upload.getStyleClass().addAll("telemetry-network-rate");

        VBox header = new VBox(2.0, title, live);
        HBox networkRates = new HBox(24.0, download, upload);

        getChildren().addAll(header, systemTitle, cpu, ram, gpuTitle, gpu, vram, gpuTemp, networkTitle, networkRates, ip);
    }

    public void update(CoreVisualSnapshot coreVisualSnapshot) {
        double ramPercentage;
        double vramPercentage;

        var system = coreVisualSnapshot.systemSnapshot();
        var gpuSnapshot = coreVisualSnapshot.gpuSnapshot();
        var networkSnapshot = coreVisualSnapshot.networkSnapshot();

        cpu.setValue("%.0f %%".formatted(system.cpuUsage()), system.cpuUsage());
        ramPercentage = system.ramUsedGb() / system.ramTotalGb() * 100.0;
        ram.setValue("%.1f / %.0f GB".formatted(system.ramUsedGb(), system.ramTotalGb()), ramPercentage);
        gpu.setValue("%.0f %%".formatted(gpuSnapshot.usage()), gpuSnapshot.usage());
        vramPercentage = gpuSnapshot.vramUsedGb() / gpuSnapshot.vramTotalGb() * 100.0;
        vram.setValue("%.1f / %.0f GB".formatted(gpuSnapshot.vramUsedGb(), gpuSnapshot.vramTotalGb()), vramPercentage);
        gpuTemp.setProgress("%.0f °C".formatted(gpuSnapshot.temperature()), normalizeTemperature(gpuSnapshot.temperature()));
        ip.setText("LOCAL // " + networkSnapshot.localIp());
        download.setText("↓ %.1f Mbps".formatted(networkSnapshot.downloadMbps()));
        upload.setText("↑ %.1f Mbps".formatted(networkSnapshot.uploadMbps()));
    }

    private static Label sectionTitle(String text) {
        Label label = new Label(text);

        label.getStyleClass().add("telemetry-section-title");
        return label;
    }

    private static double normalizeTemperature(double temperature) {
        double min = 30.0;
        double max = 95.0;

        return Math.clamp((temperature - min) / (max - min), 0.0, 1.0);
    }
}

package com.fuad.view;

import com.fuad.presentation.core.CoreVisualSnapshot;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
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

        Label networkTitle = new Label("NETWORK");
        networkTitle.getStyleClass().addAll("core-section-title");

        ip.getStyleClass().addAll("core-network-value");
        download.getStyleClass().addAll("core-network-value");
        upload.getStyleClass().addAll("core-network-value");

        getChildren().addAll(title, cpu, ram, gpu, vram, gpuTemp, networkTitle, ip, download, upload);
    }

    public void update(CoreVisualSnapshot coreVisualSnapshot) {
        var system = coreVisualSnapshot.systemSnapshot();
        var gpuSnapshot = coreVisualSnapshot.gpuSnapshot();
        var networkSnapshot = coreVisualSnapshot.networkSnapshot();

        cpu.setValue("%.0f %%".formatted(system.cpuUsage()), system.cpuUsage());
        double ramPercentage = system.ramUsedGb() / system.ramTotalGb() * 100.0;
        ram.setValue("%.1f / %.0f GB".formatted(system.ramUsedGb(), system.ramTotalGb()), ramPercentage);
        gpu.setValue("%.0f %%".formatted(gpuSnapshot.usage()), gpuSnapshot.usage());
        double vramPercentage = gpuSnapshot.vramUsedGb() / gpuSnapshot.vramTotalGb() * 100.0;
        vram.setValue("%.1f / %.0f GB".formatted(gpuSnapshot.vramUsedGb(), gpuSnapshot.vramTotalGb()), vramPercentage);
        gpuTemp.setValue("%.0f °C".formatted(gpuSnapshot.temperature()), gpuSnapshot.temperature());
        ip.setText("IP     " + networkSnapshot.localIp());
        download.setText("DOWN  %.1f Mbps".formatted(networkSnapshot.downloadMbps()));
        upload.setText("UP   %.1f Mbps".formatted(networkSnapshot.uploadMbps()));
    }
}

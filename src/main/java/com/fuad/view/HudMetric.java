package com.fuad.view;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;

public class HudMetric extends VBox {
    private static final double BAR_WIDTH = 260.0;
    private static final double BAR_HEIGHT = 5.0;
    private final Label title = new Label();
    private final Label value = new Label();
    private final Rectangle fill = new Rectangle(0.0, BAR_HEIGHT);

    HudMetric(String titleText) {
        setSpacing(6.0);

        title.setText(titleText);

        title.getStyleClass().add("core-metric-title");
        value.getStyleClass().add("core-metric-value");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(8.0, title, spacer, value);
        header.setAlignment(Pos.BASELINE_LEFT);
        header.setMaxWidth(Double.MAX_VALUE);

        Rectangle track = new Rectangle(BAR_WIDTH, BAR_HEIGHT);
        track.getStyleClass().add("core-metric-track");

        fill.getStyleClass().add("core-metric-fill");

        Pane bar = new Pane(track, fill);
        bar.setMinSize(BAR_WIDTH, BAR_HEIGHT);
        bar.setPrefSize(BAR_WIDTH, BAR_HEIGHT);

        getChildren().addAll(header, bar);
    }

    protected void setValue(String text, double percentage) {
        double normalized;

        value.setText(text);
        normalized = Math.clamp(percentage / 100.0, 0.0, 1.0);
        fill.setWidth(BAR_WIDTH * normalized);
    }

    protected void setProgress(String text, double progress) {
        value.setText(text);
        setProgress(progress);
    }

    private void setProgress(double progress) {
        double normalized;

        normalized = Math.clamp(progress, 0.0, 1.0);
        fill.setWidth(BAR_WIDTH * normalized);
    }
}

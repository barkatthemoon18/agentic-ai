package com.fuad.view;

import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;

public class HudMetric extends VBox {
    private static final double BAR_WIDTH = 260.0;
    private static final double BAR_HEIGHT = 5.0;
    private final Label value = new Label();
    private final Rectangle fill = new Rectangle(0, BAR_HEIGHT);

    HudMetric(String titleText) {
        setSpacing(5.0);
        Label title = new Label(titleText);
        title.getStyleClass().add("core-metric-title");
        value.getStyleClass().add("core-metric-value");
        Rectangle track = new Rectangle(BAR_WIDTH, BAR_HEIGHT);
        track.getStyleClass().add("core-metric-track");
        fill.getStyleClass().add("core-metric-fill");
        Pane bar = new Pane(track, fill);
        bar.setMinSize(BAR_WIDTH, BAR_HEIGHT);
        bar.setPrefSize(BAR_WIDTH, BAR_HEIGHT);
        getChildren().addAll(title, value, bar);
    }

    void setValue(String text, double percentage) {
        value.setText(text);
        double normalized = Math.clamp(percentage / 100.0, 0.0, 1.0);
        fill.setWidth(BAR_WIDTH * normalized);
    }
}

package com.fuad.view;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.ArcType;

public class HudBackground extends Region {
    private static final Color GRID = Color.rgb(55, 190, 218, 0.055);
    private static final Color GRID_MAJOR = Color.rgb(70, 210, 235, 0.10);
    private static final Color ACCENT = Color.rgb(120, 236, 255, 0.22);
    private static final Color ACCENT_FAINT = Color.rgb(34, 207, 245, 0.10);
    private final Canvas canvas = new Canvas();

    public HudBackground() {
        setMouseTransparent(true);
        getChildren().add(canvas);
        widthProperty().addListener(observable -> redraw());
    }

    @Override
    protected void layoutChildren() {
        double width = getWidth();
        double height = getHeight();
        canvas.setWidth(width);
        canvas.setHeight(height);
        redraw();
    }

    private void redraw() {
        double width = canvas.getWidth();
        double height = canvas.getHeight();

        if (width <= 0.0 || height <= 0.0) {
            return;
        }
        GraphicsContext graphicsContext = canvas.getGraphicsContext2D();
        graphicsContext.clearRect(0, 0, width, height);
        drawGrid(graphicsContext, width, height);
        drawCrossMarkers(graphicsContext, width, height);
        drawCornerGeometry(graphicsContext, width, height);
        drawCentralGuides(graphicsContext, width, height);
    }

    private void drawGrid(GraphicsContext graphicsContext, double width, double height) {
        final double minor = 40.0;
        final double major = 200.0;

        graphicsContext.setLineWidth(1.0);
        for (double x = 0; x <= width; x += minor) {
            boolean majorLine = Math.round(x) % major == 0;
            graphicsContext.setStroke(majorLine ? GRID_MAJOR : GRID);
            graphicsContext.strokeLine(x, 0, x, height);
        }
        for (double y = 0; y <= height; y += minor) {
            boolean majorLine = Math.round(y) % major == 0;
            graphicsContext.setStroke(majorLine ? GRID_MAJOR : GRID);
            graphicsContext.strokeLine(0, y, width, y);
        }
    }

    private void drawCrossMarkers(GraphicsContext graphicsContext, double width, double height) {
        graphicsContext.setStroke(ACCENT_FAINT);
        graphicsContext.setLineWidth(1.0);

        final double spacing = 120.0;
        final double size = 4.0;

        for (double x = 60.0; x < width; x += spacing) {
            for (double y = 60.0; y < height; y +=  spacing) {
                graphicsContext.strokeLine(x - size, y, x + size, y);
                graphicsContext.strokeLine(x, y - size, x, y + size);
            }
        }
    }

    private void drawCornerGeometry(GraphicsContext graphicsContext, double width, double height) {
        graphicsContext.setStroke(ACCENT);
        graphicsContext.setLineWidth(1.4);

        double margin = 18.0;
        double horizontal = 180.0;
        double vertical = 78.0;
        double cut = 18.0;

        graphicsContext.beginPath();
        graphicsContext.moveTo(margin, margin + vertical);
        graphicsContext.lineTo(margin, margin + cut);
        graphicsContext.lineTo(margin + cut, margin);
        graphicsContext.stroke();
        graphicsContext.beginPath();
        graphicsContext.lineTo(width - margin - cut, margin);
        graphicsContext.lineTo(width - margin, margin + vertical);
        graphicsContext.stroke();
        graphicsContext.beginPath();
        graphicsContext.moveTo(margin, height - margin - vertical);
        graphicsContext.lineTo(margin, height - margin - cut);
        graphicsContext.lineTo(margin + cut, height - margin);
        graphicsContext.lineTo(margin + horizontal, height - margin);
        graphicsContext.stroke();
        graphicsContext.beginPath();
        graphicsContext.moveTo(width - margin - horizontal, height - margin);
        graphicsContext.lineTo(width - margin - cut, height - margin);
        graphicsContext.lineTo(width - margin, height - margin - cut);
        graphicsContext.lineTo(width - margin, height - margin - vertical);
        graphicsContext.stroke();
    }

    private void drawCentralGuides(GraphicsContext graphicsContext, double width, double height) {
        double cx = width / 2.0;
        double cy = height / 2.0;

        graphicsContext.setStroke(ACCENT_FAINT);
        graphicsContext.setLineWidth(1.0);

        double outer = 700.0;
        double inner = 610.0;

        graphicsContext.strokeOval(cx - outer / 2.0, cy - outer / 2.0, outer, outer);
        graphicsContext.strokeOval(cx - inner / 2.0, cy - inner / 2.0, inner, inner);

        graphicsContext.setStroke(ACCENT);
        graphicsContext.setLineWidth(1.5);
        graphicsContext.strokeArc(cx - 365.0, cy - 365.0, 730.0, 730.0, 28.0, 36.0, ArcType.OPEN);
        graphicsContext.strokeArc(cx - 365.0, cy - 365.0, 730.0, 730.0, 208.0, 36.0, ArcType.OPEN);

        graphicsContext.setStroke(ACCENT_FAINT);
        graphicsContext.strokeLine(cx - 390.0, cy, cx - 320.0, cy);

        graphicsContext.strokeLine(cx + 320.0, cy, cx + 390.0, cy);
        graphicsContext.strokeLine(cx, cy + 320.0, cx, cy + 390.0);
    }
}

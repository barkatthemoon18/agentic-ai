package com.fuad.view;

import com.fuad.presentation.core.AssistantVisualState;
import com.fuad.presentation.core.CoreVisualProfile;
import com.fuad.presentation.core.CoreVisualSnapshot;
import javafx.animation.AnimationTimer;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.ArcType;

public class AresCoreView extends StackPane {
    private static final double CORE_RADIUS = 88.0;
    private final Canvas canvas = new Canvas(620, 620);
    private final Label coreTitle = new Label("ARES");
    private final Label state = new Label("● IDLE");
    private final VBox identity = new VBox(4.0, coreTitle, state);
    private AssistantVisualState visualState = AssistantVisualState.IDLE;
    private double innerRotation;
    private double middleRotation;
    private double outerRotation;
    private double sweepRotation;
    private double time;
    private final AnimationTimer animationTimer = new AnimationTimer() {
        private long previous;

        @Override
        public void handle(long now) {
            if (previous == 0) {
                previous = now;
                return;
            }
            double delta = (now - previous) / 1_000_000_000.0;
            previous = now;
            time += delta;
            CoreVisualProfile profile = profile();
            innerRotation = normalize(innerRotation + delta * profile.innerSpeed());
            middleRotation = normalize(middleRotation + delta * profile.middleSpeed());
            outerRotation = normalize(outerRotation + delta * profile.outerSpeed());
            sweepRotation = normalize(sweepRotation + delta * 42.0);
            draw();
        }
    };

    public AresCoreView() {
        setAlignment(Pos.CENTER);
        getStyleClass().add("ares-core");
        coreTitle.getStyleClass().add("ares-core-title");
        state.getStyleClass().add("ares-core-state");
        identity.setAlignment(Pos.CENTER);
        identity.setTranslateY(48.0);
        getChildren().addAll(canvas, identity);
        animationTimer.start();
    }

    public void update(CoreVisualSnapshot visualSnapshot) {
        visualState = visualSnapshot.assistantVisualState();
        state.setText("● " + visualState.name());
    }

    private void draw() {
        GraphicsContext graphicsContext =  canvas.getGraphicsContext2D();
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        graphicsContext.clearRect(0, 0, w, h);
        double cx = w / 2.0;
        double cy = h / 2.0;

        CoreVisualProfile profile = profile();

        drawOuterGuides(graphicsContext, cx, cy);
        drawTicks(graphicsContext, cx, cy);
        drawCoreHalo(graphicsContext, cx, cy, profile);

        drawRingGuide(graphicsContext, cx, cy, 145, 0.07);
        drawRingGuide(graphicsContext, cx, cy, 190, 0.055);
        drawRingGuide(graphicsContext, cx, cy, 235, 0.04);

        drawSegmentedRing(graphicsContext, cx, cy, 235.0, outerRotation, 6, 38.0, 1.5, 0.38);
        drawSegmentedRing(graphicsContext, cx, cy, 190.0, middleRotation, 12, 17.0, 2.2, 0.60);
        drawSegmentedRing(graphicsContext, cx, cy, 145.0, innerRotation, 8, 24.0, 3.0, 0.82);

        drawSweep(graphicsContext, cx, cy, profile);

        drawCore(graphicsContext, cx, cy);
        drawCorePulse(graphicsContext, cx, cy);
        drawWaveform(graphicsContext, cx, cy, profile);
    }

    private void drawRingGuide(GraphicsContext graphicsContext, double cx, double cy, double radius, double opacity) {
        graphicsContext.setStroke(Color.rgb(120, 236, 255, opacity));
        graphicsContext.setLineWidth(1.0);
        graphicsContext.strokeOval(cx - radius, cy - radius, radius * 2.0, radius * 2.0);
    }

    private void drawOuterGuides(GraphicsContext graphicsContext, double cx, double cy) {
        graphicsContext.setStroke(Color.rgb(120, 236, 255, 0.10));
        graphicsContext.setLineWidth(1.0);

        double guideOuterRadius = 285.0;
        double guideInnerRadius = 265.0;

        graphicsContext.strokeOval(cx - guideOuterRadius, cy - guideOuterRadius, guideOuterRadius * 2.0,
                guideOuterRadius * 2.0);
        graphicsContext.strokeOval(cx - guideInnerRadius, cy - guideInnerRadius, guideInnerRadius * 2.0,
                guideInnerRadius * 2.0);

        graphicsContext.setStroke(Color.rgb(120, 236, 255, 0.12));

        double axisInner = 275.0;
        double axisOuter = 305.0;

        graphicsContext.strokeLine(cx - axisOuter, cy, cx - axisInner, cy);
        graphicsContext.strokeLine(cx + axisInner, cy, cx + axisOuter, cy);
        graphicsContext.strokeLine(cx, cy - axisOuter, cx, cy - axisInner);
        graphicsContext.strokeLine(cx, cy + axisInner, cx, cy + axisOuter);

        graphicsContext.setStroke(Color.rgb(120, 236, 255, 0.16));
        graphicsContext.setLineWidth(1.2);

        double diagnosticsRadius = 300.0;

        graphicsContext.strokeArc(cx - diagnosticsRadius, cy - diagnosticsRadius, diagnosticsRadius * 2.0,
                diagnosticsRadius * 2.0, 25.0, 42.0, ArcType.OPEN);

        graphicsContext.strokeArc(cx - diagnosticsRadius, cy - diagnosticsRadius, diagnosticsRadius * 2.0,
                diagnosticsRadius * 2.0, 205, 42.0, ArcType.OPEN);
    }

    private void drawSegmentedRing(GraphicsContext graphicsContext, double cx, double cy, double radius, double rotation,
                                   int segments, double arcLength, double lineWidth, double opacity) {
        graphicsContext.setStroke(Color.rgb(120, 236, 255, opacity));
        graphicsContext.setLineWidth(lineWidth);
        double step = 360.0 / segments;
        for (int i = 0; i < segments; i++) {
            double start = rotation + i * step;
            graphicsContext.strokeArc(cx - radius, cy - radius, radius * 2.0, radius * 2.0, start, arcLength,
                    ArcType.OPEN);
        }
    }

    private void drawTicks(GraphicsContext graphicsContext, double cx, double cy) {
        graphicsContext.setStroke(Color.rgb(120, 236, 255, 0.18));
        graphicsContext.setLineWidth(1.0);

        double inner = 255.0;

        for (int degrees = 0; degrees < 360; degrees += 10) {
            double angle = Math.toRadians(degrees);
            double x1 = cx + Math.cos(angle) * inner;
            double y1 = cy + Math.sin(angle) * inner;
            double length = degrees % 30 == 0 ? 14.0 : 7.0;
            double x2 = cx + Math.cos(angle) * (inner + length);
            double y2 = cy + Math.sin(angle) * (inner + length);
            graphicsContext.strokeLine(x1, y1, x2, y2);
        }
    }

    private void drawCoreHalo(GraphicsContext graphicsContext, double cx, double cy, CoreVisualProfile profile) {
        double pulse = (Math.sin(time * 2.2) + 1.0) / 2.0;
        double intensity = profile.corePulse() * (0.55 + pulse * 0.45);
        for (int i = 4; i >= 1; i--) {
             double radius = 88.0 + i * 13.0;
             double alpha = intensity * Math.pow(1.0 - i / 5.0, 2.0);
             graphicsContext.setFill(Color.rgb(34, 207, 245, alpha));
             graphicsContext.fillOval(cx - radius, cy - radius, radius * 2.0, radius * 2.0);
        }
    }

    private void drawCore(GraphicsContext graphicsContext, double cx, double cy) {
        final double radius = CORE_RADIUS;
        graphicsContext.setFill(Color.rgb(7, 31, 45, 0.92));
        graphicsContext.fillOval(cx - radius, cy - radius, radius * 2.0, radius * 2.0);
        graphicsContext.setStroke(Color.rgb(120, 236, 255, 0.88));
        graphicsContext.setLineWidth(1.8);
        graphicsContext.strokeOval(cx - radius, cy - radius, radius * 2.0, radius * 2.0);

        /*
         * Segundo círculo interior.
         */
        double inner = radius - 11.0;
        graphicsContext.setStroke(Color.rgb(120, 236, 255, 0.15));
        graphicsContext.setLineWidth(1.0);
        graphicsContext.strokeOval(cx - inner, cy - inner, inner * 2.0, inner * 2.0);
    }

    private void drawSweep(GraphicsContext graphicsContext, double cx, double cy, CoreVisualProfile profile) {
        if (profile.sweepOpacity() <= 0.0) {
            return;
        }
        final double radius = 265.0;
        graphicsContext.setStroke(Color.rgb(120, 236, 255, profile.sweepOpacity()));
        graphicsContext.setLineWidth(2.0);
        graphicsContext.strokeArc(cx - radius, cy - radius, radius * 2.0, radius * 2.0, sweepRotation, 24.0, ArcType.OPEN);
    }

    private void drawWaveform(GraphicsContext graphicsContext, double cx, double cy, CoreVisualProfile profile) {
        double waveformY = cy - 22.0;

        graphicsContext.setStroke(Color.rgb(120, 236, 255, profile.waveformOpacity()));
        graphicsContext.setLineWidth(1.0);
        graphicsContext.strokeLine(cx - 115.0, waveformY, cx + 115.0, waveformY);
        double width = 222.0;
        double startX = cx - width / 2.0;
        graphicsContext.beginPath();
        for (int i = 0; i <= 72; i++) {
            double x = startX + width * i / 72.0;
            double envelope = Math.sin(Math.PI * i / 72.0);
            double signal = Math.sin(time * profile.waveformFrequency() + i * 0.52) * 0.68 +
                    Math.sin(time * profile.waveformFrequency() * 1.7 + i * 0.23);
            double y = waveformY + signal * envelope * profile.waveformAmplitude();
            if (i == 0) {
                graphicsContext.moveTo(x, y);
            }
            else {
                graphicsContext.lineTo(x, y);
            }
        }
        graphicsContext.stroke();
    }

    private void drawCorePulse(GraphicsContext graphicsContext, double cx, double cy) {
        double pulse = (Math.sin(time * 2.2) + 1.0) / 2.0;
        double radius = 2.0 + pulse * 1.5;

        graphicsContext.setFill(Color.rgb(120, 236, 255, 0.45 + pulse * 0.35));
        graphicsContext.fillOval(cx - radius, (cy + 8.0) - radius, radius * 2.0, radius * 2.0);
    }

    private CoreVisualProfile profile() {
        return switch (visualState) {
            case IDLE -> new CoreVisualProfile(4.0, -2.0, 1.0, 5.0,
                    1.8, 0.48, 0.05, 0.0);

            case LISTENING -> new CoreVisualProfile(1.0, -6.0, 3.0, 24.0,
                    5.0, 0.95, 0.18, 0.12);

            case PROCESSING -> new CoreVisualProfile(30.0, -18.0, 7.0,
                    7.0, 7.0, 0.60, 0.12, 0.80);

            case EXECUTING -> new CoreVisualProfile(20.0, -10.0, 5.0,
                    11.0, 3.5,0.72, 0.14, 0.48);

            case SPEAKING -> new CoreVisualProfile(8.0, -4.0, 2.0, 28.0,
                    6.2, 1.00,0.20, 0.08);

            case DEGRADED -> new CoreVisualProfile(2.0, -1.0, 0.5, 2.0,
                    1.2, 0.28,0.02, 0.0);
        };
    }

    private static double normalize(double angle) {
        angle %= 360.0;

        return angle < 0.0 ? angle + 360.0 : angle;
    }

    public void dispose() {
        animationTimer.stop();
    }
}

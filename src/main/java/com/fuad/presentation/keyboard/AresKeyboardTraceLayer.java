package com.fuad.presentation.keyboard;

import javafx.animation.AnimationTimer;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.ArcTo;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.scene.shape.CubicCurveTo;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.Line;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.PathElement;
import javafx.scene.shape.StrokeLineCap;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleFunction;
import java.util.function.Supplier;

/**
 * Engraved curves with independently timed light trails and touch feedback.
 */
final class AresKeyboardTraceLayer extends Group {
    private static final Color ETCH = Color.web("#7699a7");
    private static final Color LIGHT = Color.web("#78ecff");
    private static final Color ACTIVE = Color.web("#39d5ff");
    private static final double HUB_X = 158;
    private static final double HUB_Y = 216;
    private static final double COMMUNICATION_BASELINE = 374;
    private final List<Glimmer> glimmers = new ArrayList<>();
    private final List<LightTrail> trails = new ArrayList<>();
    private final List<TouchPulse> pulses = new ArrayList<>();
    private final Ellipse pointerHalo = new Ellipse(30, 20);
    private final Group communicationBars = new Group();
    private final List<Line> bars = new ArrayList<>();
    private final ChangeListener<Window> windowListener = (observable, previous, next) -> attachWindow(next);
    private final ChangeListener<Boolean> windowStateListener = (observable, previous, next) -> updateAnimation();
    private Scene attachedScene;
    private Window attachedWindow;
    private boolean animationsEnabled = true;
    private boolean running;
    private double elapsedSeconds;
    private long lastFrame;
    private Point2D pointer;
    private int nextPulse;
    private final AnimationTimer timer = new AnimationTimer() {
        @Override
        public void handle(long now) {
            if (lastFrame != 0) {
                elapsedSeconds += (now - lastFrame) / 1_000_000_000.0;
            }
            lastFrame = now;
            updateReflections();
        }
    };

    AresKeyboardTraceLayer() {
        setManaged(false);
        setMouseTransparent(true);
        drawEngravings();
        drawReflections();
        drawLightTrails();
        drawInteractionLights();
        drawCommunicationBars();
        updateReflections();
        sceneProperty().addListener((observable, previous, next) -> attachScene(next));
        visibleProperty().addListener(windowStateListener);
    }

    void setAnimationsEnabled(boolean enabled) {
        animationsEnabled = enabled;
        updateAnimation();
    }

    boolean areAnimationsEnabled() {
        return animationsEnabled;
    }

    /**
     * The same deterministic event schedule drives live rendering and exported frames.
     */
    void jumpTo(Duration time) {
        if (time == null || !Double.isFinite(time.toMillis()) || time.lessThan(Duration.ZERO)) {
            throw new IllegalArgumentException("Animation time must be a finite, non-negative duration");
        }
        elapsedSeconds = time.toSeconds();
        lastFrame = 0;
        updateReflections();
    }

    Duration getAnimationTime() {
        return Duration.seconds(elapsedSeconds);
    }

    /** A sibling of this layer so the footer can place it above the surface. */
    Group communicationBars() {
        return communicationBars;
    }

    void respondToPointer(Point2D position) {
        pointer = position;
        pointerHalo.setCenterX(position.getX());
        pointerHalo.setCenterY(position.getY());
        pointerHalo.setOpacity(0.20);
    }

    void clearPointer() {
        pointer = null;
        pointerHalo.setOpacity(0);
    }

    void highlightAt(Point2D position) {
        LightTrail closest = null;
        double distance = Double.POSITIVE_INFINITY;
        for (LightTrail trail : trails) {
            double candidate = trail.distanceTo(position);
            if (candidate < distance) {
                distance = candidate;
                closest = trail;
            }
        }
        if (closest != null && elapsedSeconds - closest.triggeredAt > 0.45) {
            closest.triggeredAt = elapsedSeconds;
        }
        updateReflections();
    }

    void pulseAt(Point2D position) {
        TouchPulse pulse = pulses.get(nextPulse);
        nextPulse = (nextPulse + 1) % pulses.size();
        pulse.light.setLayoutX(position.getX());
        pulse.light.setLayoutY(position.getY());
        pulse.startedAt = elapsedSeconds;
        highlightAt(position);
    }

    private void drawEngravings() {
        // Paired, broken arcs stay inside the gutters around the five action keys.
        etchedArc(129, 50, 310, 0.13);
        etchedArc(124, 214, 258, 0.47);
        etchedArc(127, 222, 253, 0.20);
        etchedArc(130, 237, 260, 0.13);
        etchedArc(124, 271, 319, 0.44);
        etchedArc(127, 275, 308, 0.19);
        etchedArc(124, 37, 79, 0.43);
        etchedArc(127, 46, 73, 0.18);
        etchedArc(124, 92, 132, 0.37);
        etchedArc(127, 100, 126, 0.17);
        etchedArc(124, 147, 205, 0.37);
        etchedArc(127, 157, 193, 0.17);
        etchedArc(64, 219, 265, 0.42);
        etchedArc(62, 230, 258, 0.18);
        etchedArc(64, 273, 319, 0.43);
        etchedArc(64, 37, 79, 0.35);
        etchedArc(64, 92, 133, 0.39);
        etchedArc(62, 100, 128, 0.17);
        etchedArc(64, 148, 207, 0.30);
        etchedArc(25, 218, 265, 0.28);
        etchedArc(25, 275, 322, 0.26);
        etchedArc(25, 38, 83, 0.24);
        etchedArc(25, 95, 135, 0.23);

        // Small notches occupy the ring gutter, away from the touch targets.
        for (double angle : new double[]{225, 227, 229, 279, 281, 283, 105, 107, 109}) {
            Point2D a = KeyboardGeometry.polar(HUB_X, HUB_Y, 129, angle);
            Point2D b = KeyboardGeometry.polar(HUB_X, HUB_Y, 132, angle);
            etch(0.28, new MoveTo(a.getX(), a.getY()), new LineTo(b.getX(), b.getY()));
        }
        etch(0.29, new MoveTo(284, 92), new CubicCurveTo(402, 63, 573, 67, 716, 88));
        etch(0.15, new MoveTo(344, 80), new CubicCurveTo(430, 64, 559, 67, 646, 79));
        etch(0.27, new MoveTo(300, 97), new CubicCurveTo(429, 68, 585, 72, 710, 94));
        etch(0.15, new MoveTo(529, 80), new CubicCurveTo(592, 82, 654, 89, 709, 99));
        etch(0.23, new MoveTo(734, 130), new CubicCurveTo(725, 193, 724, 275, 735, 329));
        etch(0.12, new MoveTo(731, 167), new CubicCurveTo(726, 215, 728, 275, 732, 303));

        // Bowed brackets give the primary action space around its illuminated rim.
        etch(0.47, new MoveTo(295, 170), new CubicCurveTo(286, 180, 286, 199, 295, 208));
        etch(0.23, new MoveTo(290, 173), new CubicCurveTo(281, 183, 282, 198, 290, 206));
        etch(0.47, new MoveTo(707, 170), new CubicCurveTo(716, 180, 716, 199, 707, 208));
        etch(0.23, new MoveTo(712, 173), new CubicCurveTo(721, 183, 720, 198, 712, 206));

        // Lower incisions echo the arched chassis beneath the contextual suggestions.
        etch(0.42, new MoveTo(290, 382), new CubicCurveTo(359, 367, 415, 365, 460, 370));
        etch(0.22, new MoveTo(316, 386), new CubicCurveTo(370, 375, 415, 372, 455, 375));
        etch(0.40, new MoveTo(584, 370), new CubicCurveTo(635, 366, 677, 372, 716, 382));
        etch(0.20, new MoveTo(590, 376), new CubicCurveTo(635, 373, 675, 378, 699, 386));
    }

    private void drawReflections() {
        // Each local reflection stays in place; only its light intensity changes.
        arcReflection(124, 226, 240, 0.4, 6.3, 17, 0.96);
        arcReflection(127, 279, 290, 2.1, 8.7, 31, 0.90);
        arcReflection(64, 240, 257, 3.8, 7.9, 47, 0.85);
        arcReflection(64, 101, 121, 1.4, 9.3, 71, 0.88);
        arcReflection(124, 167, 180, 4.8, 8.1, 89, 0.88);
        arcReflection(124, 49, 63, 6.3, 10.3, 97, 0.84);
        seamReflection(401, 371, 28, -0.065, 0.3, 6.7, 107, 0.94);
        seamReflection(607, 372, 24, 0.025, 3.2, 9.3, 131, 0.88);
        seamReflection(677, 377, 24, 0.16, 5.6, 11.7, 157, 0.86);
        seamReflection(529, 80, 24, 0.045, 1.1, 8.7, 181, 0.85);
        seamReflection(626, 86, 21, 0.12, 4.8, 10.1, 199, 0.82);
    }

    private void drawLightTrails() {
        // Short, offset sweeps follow the etched arcs instead of flashing the whole panel.
        orbitTrail(129, 215, 260, 0.2, 6.1, 211);
        orbitTrail(129, 267, 321, 2.4, 7.7, 223);
        orbitTrail(129, 144, 208, 1.2, 8.3, 227);
        orbitTrail(129, 88, 137, 3.2, 6.9, 229);
        orbitTrail(129, 36, 83, 4.5, 8.9, 233);
        orbitTrail(66, 218, 263, 1.7, 5.9, 239);
        orbitTrail(66, 94, 135, 3.8, 7.3, 241);
        curveTrail(294, 97, 407, 65, 579, 69, 711, 94, 0.0, 6.7, 251);
        curveTrail(706, 88, 576, 64, 430, 62, 344, 80, 2.8, 9.1, 257);
        curveTrail(271, 124, 287, 191, 287, 281, 274, 342, 0.7, 7.1, 263);
        curveTrail(731, 346, 712, 280, 712, 174, 731, 98, 2.0, 8.1, 269);
        curveTrail(289, 380, 347, 365, 405, 363, 460, 370, 0.9, 5.7, 271);
        curveTrail(715, 381, 671, 367, 628, 365, 584, 370, 2.2, 6.3, 277);
        curveTrail(754, 364, 823, 352, 936, 351, 1009, 364, 3.4, 8.7, 281);
        curveTrail(292, 177, 278, 189, 279, 209, 293, 222, 1.6, 5.3, 283);
        curveTrail(704, 222, 718, 208, 718, 189, 704, 177, 3.0, 6.1, 293);
    }

    private void orbitTrail(double radius, double start, double end, double first, double interval, long seed) {
        addTrail(() -> path(arc(radius, start, end)),
                t -> KeyboardGeometry.polar(HUB_X, HUB_Y, radius, start + (end - start) * t),
                first, interval, seed, true);
    }

    private void curveTrail(double x, double y, double ax, double ay, double bx, double by,
                            double endX, double endY, double first, double interval, long seed) {
        addTrail(() -> path(new MoveTo(x, y), new CubicCurveTo(ax, ay, bx, by, endX, endY)), t -> {
            double u = 1 - t;
            return new Point2D(u * u * u * x + 3 * u * u * t * ax + 3 * u * t * t * bx + t * t * t * endX,
                    u * u * u * y + 3 * u * u * t * ay + 3 * u * t * t * by + t * t * t * endY);
        }, first, interval, seed, false);
    }

    private void addTrail(Supplier<Path> geometry, DoubleFunction<Point2D> pointAt,
                          double first, double interval, long seed, boolean radial) {
        Path track = geometry.get();
        track.setStroke(LIGHT);
        track.setStrokeWidth(0.55);
        track.setOpacity(0.13);
        Path halo = geometry.get();
        halo.setStroke(ACTIVE);
        halo.setStrokeWidth(3.6);
        halo.setEffect(new GaussianBlur(4.5));
        Path core = geometry.get();
        core.setStroke(LIGHT.interpolate(Color.WHITE, 0.27));
        core.setStrokeWidth(1.1);
        Circle spark = new Circle(1.45, Color.web("#d7fbff"));
        spark.setEffect(new DropShadow(7, ACTIVE));
        Group light = new Group(halo, core, spark);
        light.setId("keyboard-trail-" + seed);
        light.setOpacity(0);
        if (radial) {
            track.setClip(AresKeyboardView.radialClip());
            light.setClip(AresKeyboardView.radialClip());
        }
        getChildren().addAll(track, light);
        trails.add(new LightTrail(light, halo, core, spark, pointAt, first, interval, seed));
    }

    private void drawInteractionLights() {
        pointerHalo.setId("keyboard-pointer-halo");
        pointerHalo.setFill(ACTIVE);
        pointerHalo.setEffect(new GaussianBlur(18));
        pointerHalo.setOpacity(0);
        getChildren().add(pointerHalo);
        // Reuse a bounded pool so repeated touches do not accumulate nodes or timers.
        for (int i = 0; i < 4; i++) {
            Arc outer = pulseArc(22, 126);
            Arc inner = pulseArc(202, 94);
            inner.setStrokeWidth(0.8);
            Group light = new Group(outer, inner);
            light.setId("keyboard-touch-pulse-" + i);
            light.setEffect(new DropShadow(6, ACTIVE));
            light.setOpacity(0);
            getChildren().add(light);
            pulses.add(new TouchPulse(light, outer, inner));
        }
    }

    private static Arc pulseArc(double start, double length) {
        Arc arc = new Arc(0, 0, 10, 10, start, length);
        arc.setType(ArcType.OPEN);
        arc.setFill(null);
        arc.setStroke(LIGHT);
        arc.setStrokeWidth(1.25);
        arc.setStrokeLineCap(StrokeLineCap.ROUND);
        return arc;
    }

    private void drawCommunicationBars() {
        communicationBars.setId("keyboard-communication-bars");
        communicationBars.setManaged(false);
        communicationBars.setMouseTransparent(true);
        communicationBars.setAccessibleText("Espera de comunicación");
        communicationBars.setEffect(new DropShadow(5, ACTIVE.deriveColor(0, 1, 1, 0.55)));
        for (int i = 0; i < 27; i++) {
            double x = AresKeyboardView.WIDTH / 2 + (i - 13) * 3.5;
            Line bar = new Line(x, COMMUNICATION_BASELINE, x, COMMUNICATION_BASELINE);
            bar.setId("communication-bar-" + i);
            bar.setStroke(LIGHT);
            bar.setStrokeWidth(i % 3 == 0 ? 1.15 : 0.8);
            bar.setStrokeLineCap(StrokeLineCap.ROUND);
            bars.add(bar);
            communicationBars.getChildren().add(bar);
        }
    }

    private void arcReflection(double radius, double start, double end, double first, double interval,
                               long seed, double peak) {
        Path halo = path(arc(radius, start, end));
        halo.setStroke(ACTIVE.deriveColor(0, 1, 1, 0.64));
        halo.setStrokeWidth(2.1);
        halo.setEffect(new GaussianBlur(4));
        Path core = path(arc(radius, start, end));
        core.setStroke(edgeLight());
        core.setStrokeWidth(0.95);
        Group light = new Group(halo, core);
        light.setClip(AresKeyboardView.radialClip());
        addGlimmer(light, first, interval, seed, peak);
    }

    private void seamReflection(double x, double y, double radius, double slope, double first, double interval,
                                long seed, double peak) {
        Ellipse haze = new Ellipse(x, y, radius, 2.2);
        haze.setFill(ACTIVE.deriveColor(0, 1, 1, 0.65));
        haze.setEffect(new GaussianBlur(8));
        haze.setRotate(Math.toDegrees(Math.atan(slope)));
        Path core = path(new MoveTo(x - radius * 0.73, y - radius * 0.73 * slope),
                new CubicCurveTo(x - radius * 0.25, y - 1.8 - radius * 0.25 * slope,
                        x + radius * 0.25, y - 1.8 + radius * 0.25 * slope,
                        x + radius * 0.73, y + radius * 0.73 * slope));
        core.setStroke(edgeLight());
        core.setStrokeWidth(0.55);
        addGlimmer(new Group(haze, core), first, interval, seed, peak);
    }

    private void addGlimmer(Group light, double first, double interval, long seed, double peak) {
        light.setOpacity(0);
        light.setId("keyboard-reflection-" + seed);
        getChildren().add(light);
        glimmers.add(new Glimmer(light, first, interval, seed, peak));
    }

    private void updateReflections() {
        for (Glimmer glimmer : glimmers) {
            double age = elapsedSeconds - glimmer.first();
            double intensity = 0;
            if (age >= 0) {
                long event = (long) Math.floor(age / glimmer.interval());
                double local = age % glimmer.interval();
                double onset = event == 0 ? 0 : 0.8 + noise(glimmer.seed(), event) * (glimmer.interval() - 4.5);
                double duration = 0.75 + noise(glimmer.seed() + 13, event) * 1.15;
                double progress = (local - onset) / duration;
                if (progress >= 0 && progress < 1) {
                    // A soft, brief arrival and a longer fading reflection, followed by darkness.
                    double attack = Math.min(1, progress / 0.22);
                    attack = attack * attack * (3 - 2 * attack);
                    double decay = 1 - Math.max(0, (progress - 0.22) / 0.78);
                    intensity = attack * decay * decay;
                }
            }
            glimmer.light().setOpacity(intensity * glimmer.peak());
        }
        updateLightTrails();
        updateTouchPulses();
        updateCommunicationBars();
    }

    private void updateLightTrails() {
        for (LightTrail trail : trails) {
            double age = elapsedSeconds - trail.first;
            double progress = -1;
            if (age >= 0) {
                long event = (long) Math.floor(age / trail.interval);
                double onset = event == 0 ? 0 : noise(trail.seed, event) * (trail.interval - 3.2);
                double duration = 1.65 + noise(trail.seed + 19, event) * 0.95;
                progress = (age % trail.interval - onset) / duration;
            }
            double interactionAge = elapsedSeconds - trail.triggeredAt;
            if (interactionAge >= 0 && interactionAge < 1.35) {
                progress = interactionAge / 1.35;
            }
            if (progress < 0 || progress >= 1) {
                trail.light.setOpacity(0);
                continue;
            }
            double proximity = pointer == null ? 0 : Math.max(0, 1 - trail.distanceTo(pointer) / 160);
            trail.light.setOpacity(Math.pow(Math.sin(Math.PI * progress), 0.6) * (0.76 + 0.24 * proximity));
            double offset = trail.dashLength - progress * (trail.length + trail.dashLength);
            trail.halo.setStrokeDashOffset(offset);
            trail.core.setStrokeDashOffset(offset);
            double headDistance = progress * (trail.length + trail.dashLength) - trail.dashLength * 0.18;
            Point2D head = trail.pointAtDistance(Math.clamp(headDistance, 0, trail.length));
            trail.spark.setCenterX(head.getX());
            trail.spark.setCenterY(head.getY());
        }
    }

    private void updateTouchPulses() {
        for (TouchPulse pulse : pulses) {
            double progress = (elapsedSeconds - pulse.startedAt) / 0.85;
            if (progress < 0 || progress >= 1) {
                pulse.light.setOpacity(0);
                continue;
            }
            double eased = 1 - Math.pow(1 - progress, 2);
            double radius = 11 + 35 * eased;
            pulse.outer.setRadiusX(radius);
            pulse.outer.setRadiusY(radius * 0.72);
            pulse.inner.setRadiusX(radius * 0.72);
            pulse.inner.setRadiusY(radius * 0.53);
            pulse.light.setRotate(progress * 48);
            pulse.light.setOpacity((1 - progress) * 0.72);
        }
    }

    private void updateCommunicationBars() {
        // A slow breath and offset ripples suggest standby, without imitating live audio.
        double breath = 0.5 + 0.5 * Math.sin(elapsedSeconds * 1.12);
        for (int i = 0; i < bars.size(); i++) {
            double envelope = Math.exp(-Math.pow((i - 13) / 7.2, 2));
            double ripple = 0.5 + 0.5 * Math.sin(i * 0.71 - elapsedSeconds * 2.1)
                    * Math.cos(i * 0.23 + elapsedSeconds * 0.47);
            double height = 2 + envelope * (2 + 5 * breath + 3 * ripple);
            Line bar = bars.get(i);
            bar.setStartY(COMMUNICATION_BASELINE - height / 2);
            bar.setEndY(COMMUNICATION_BASELINE + height / 2);
            bar.setOpacity(0.19 + envelope * (0.33 + 0.23 * breath + 0.10 * ripple));
        }
    }

    /**
     * Stable noise keeps seeks reproducible while varying each reflection's next onset and duration.
     */
    private static double noise(long seed, long event) {
        long value = seed + 0x9E3779B97F4A7C15L * (event + 1);
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return (value >>> 11) * 0x1.0p-53;
    }

    private void etchedArc(double radius, double start, double end, double opacity) {
        etch(opacity, arc(radius, start, end)).setClip(AresKeyboardView.radialClip());
    }

    private Path etch(double opacity, PathElement... elements) {
        Path path = path(elements);
        path.setStroke(ETCH);
        path.setStrokeWidth(0.45);
        path.setOpacity(opacity);
        getChildren().add(path);
        return path;
    }

    private static Path path(PathElement... elements) {
        Path path = new Path(elements);
        path.setFill(null);
        path.setStrokeLineCap(StrokeLineCap.ROUND);
        return path;
    }

    private static LinearGradient edgeLight() {
        return new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.TRANSPARENT), new Stop(0.34, LIGHT),
                new Stop(0.62, LIGHT), new Stop(1, Color.TRANSPARENT));
    }

    private static PathElement[] arc(double radius, double start, double end) {
        Point2D a = KeyboardGeometry.polar(HUB_X, HUB_Y, radius, start);
        Point2D b = KeyboardGeometry.polar(HUB_X, HUB_Y, radius, end);
        return new PathElement[]{new MoveTo(a.getX(), a.getY()),
                new ArcTo(radius, radius, 0, b.getX(), b.getY(), end - start > 180, true)};
    }

    private void attachScene(Scene scene) {
        if (attachedScene != null) {
            attachedScene.windowProperty().removeListener(windowListener);
        }
        attachedScene = scene;
        if (scene != null) {
            scene.windowProperty().addListener(windowListener);
        }
        attachWindow(scene == null ? null : scene.getWindow());
    }

    private void attachWindow(Window window) {
        if (attachedWindow != null) {
            attachedWindow.showingProperty().removeListener(windowStateListener);
            if (attachedWindow instanceof Stage previousStage) {
                previousStage.iconifiedProperty().removeListener(windowStateListener);
            }
        }
        attachedWindow = window;
        if (window != null) {
            window.showingProperty().addListener(windowStateListener);
            if (window instanceof Stage stage) {
                stage.iconifiedProperty().addListener(windowStateListener);
            }
        }
        updateAnimation();
    }

    private void updateAnimation() {
        boolean showing = attachedWindow != null && attachedWindow.isShowing();
        boolean minimized = attachedWindow instanceof Stage stage && stage.isIconified();
        boolean shouldRun = animationsEnabled && isVisible() && attachedScene != null && showing && !minimized;
        if (shouldRun != running) {
            running = shouldRun;
            lastFrame = 0;
            if (running) {
                timer.start();
            } else {
                timer.stop();
            }
        }
    }

    private record Glimmer(Group light, double first, double interval, long seed, double peak) {
    }

    private static final class LightTrail {
        private final Group light;
        private final Path halo;
        private final Path core;
        private final Circle spark;
        private final List<Point2D> samples = new ArrayList<>();
        private final double[] distances = new double[49];
        private final double length;
        private final double dashLength;
        private final double first;
        private final double interval;
        private final long seed;
        private double triggeredAt = Double.NEGATIVE_INFINITY;

        private LightTrail(Group light, Path halo, Path core, Circle spark, DoubleFunction<Point2D> pointAt,
                           double first, double interval, long seed) {
            this.light = light;
            this.halo = halo;
            this.core = core;
            this.spark = spark;
            this.first = first;
            this.interval = interval;
            this.seed = seed;
            for (int i = 0; i < distances.length; i++) {
                Point2D point = pointAt.apply(i / (double) (distances.length - 1));
                samples.add(point);
                if (i > 0) {
                    distances[i] = distances[i - 1] + point.distance(samples.get(i - 1));
                }
            }
            length = distances[distances.length - 1];
            dashLength = Math.clamp(length * 0.18, 12, 42);
            halo.getStrokeDashArray().setAll(dashLength, length + dashLength * 2);
            core.getStrokeDashArray().setAll(dashLength, length + dashLength * 2);
        }

        private double distanceTo(Point2D position) {
            double nearest = Double.POSITIVE_INFINITY;
            for (Point2D sample : samples) {
                nearest = Math.min(nearest, position.distance(sample));
            }
            return nearest;
        }

        private Point2D pointAtDistance(double distance) {
            for (int i = 1; i < distances.length; i++) {
                if (distance <= distances[i]) {
                    Point2D start = samples.get(i - 1);
                    Point2D end = samples.get(i);
                    double t = (distance - distances[i - 1]) / (distances[i] - distances[i - 1]);
                    return start.add(end.subtract(start).multiply(t));
                }
            }
            return samples.get(samples.size() - 1);
        }
    }

    private static final class TouchPulse {
        private final Group light;
        private final Arc outer;
        private final Arc inner;
        private double startedAt = Double.NEGATIVE_INFINITY;

        private TouchPulse(Group light, Arc outer, Arc inner) {
            this.light = light;
            this.outer = outer;
            this.inner = inner;
        }
    }
}

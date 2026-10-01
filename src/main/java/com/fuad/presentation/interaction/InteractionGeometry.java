package com.fuad.presentation.interaction;

import javafx.geometry.Rectangle2D;

import java.util.Objects;

final class InteractionGeometry {
    static final double HALO_PADDING = 24.0;

    private static final double OUTER_MARGIN = 8.0;
    private static final double COMPACT_HEIGHT_THRESHOLD = 800.0;

    private InteractionGeometry() {
    }

    static double panelWidth(Rectangle2D visualBounds, Surface surface) {
        return calculateWidth(visualBounds, surface).panelWidth();
    }

    static Layout calculate(Rectangle2D visualBounds, Surface surface,
                            double naturalHeight) {
        validate(visualBounds, surface, naturalHeight);
        Width width = calculateWidth(visualBounds, surface);
        double requestedHeight = naturalHeight + 2.0 * HALO_PADDING;
        double maximumHeight = visualBounds.getHeight() * surface.maximumHeightRatio;
        double desiredHeight = Math.min(requestedHeight, maximumHeight);
        double marginY = fittingMargin(visualBounds.getHeight(), desiredHeight);
        double availableHeight = Math.max(0.0,
                visualBounds.getHeight() - 2.0 * marginY);
        double stageHeight = Math.min(desiredHeight, availableHeight);
        stageHeight = Math.min(stageHeight, visualBounds.getHeight());

        double x = clamp(visualBounds.getMaxX() - width.margin() - width.stageWidth(),
                visualBounds.getMinX(), visualBounds.getMaxX() - width.stageWidth());
        double y = clamp(visualBounds.getMaxY() - marginY - stageHeight,
                visualBounds.getMinY(), visualBounds.getMaxY() - stageHeight);
        Rectangle2D stageBounds = new Rectangle2D(x, y,
                width.stageWidth(), stageHeight);
        double panelHeight = Math.max(0.0,
                stageHeight - 2.0 * HALO_PADDING);
        return new Layout(stageBounds, width.panelWidth(), panelHeight,
                surface == Surface.FREE_TEXT
                        && visualBounds.getHeight() < COMPACT_HEIGHT_THRESHOLD);
    }

    private static Width calculateWidth(Rectangle2D visualBounds, Surface surface) {
        validateBounds(visualBounds);
        Objects.requireNonNull(surface, "surface must not be null");
        double desired = clamp(surface.minimumWidth,
                visualBounds.getWidth() * surface.preferredWidthRatio,
                surface.maximumWidth);
        double margin = fittingMargin(visualBounds.getWidth(), desired);
        double available = Math.max(0.0,
                visualBounds.getWidth() - 2.0 * margin);
        double stageWidth = Math.min(desired, available);
        stageWidth = Math.min(stageWidth, visualBounds.getWidth());
        double panelWidth = Math.max(0.0,
                stageWidth - 2.0 * HALO_PADDING);
        return new Width(stageWidth, panelWidth, margin);
    }

    private static double fittingMargin(double dimension, double desiredSize) {
        return desiredSize + 2.0 * OUTER_MARGIN <= dimension ? OUTER_MARGIN : 0.0;
    }

    private static void validate(Rectangle2D visualBounds, Surface surface,
                                 double naturalHeight) {
        validateBounds(visualBounds);
        Objects.requireNonNull(surface, "surface must not be null");
        if (!Double.isFinite(naturalHeight) || naturalHeight < 0.0) {
            throw new IllegalArgumentException("naturalHeight must be finite and non-negative");
        }
    }

    private static void validateBounds(Rectangle2D bounds) {
        Objects.requireNonNull(bounds, "visualBounds must not be null");
        if (!Double.isFinite(bounds.getMinX()) || !Double.isFinite(bounds.getMinY())
                || !Double.isFinite(bounds.getWidth()) || !Double.isFinite(bounds.getHeight())
                || bounds.getWidth() <= 0.0 || bounds.getHeight() <= 0.0) {
            throw new IllegalArgumentException("visualBounds must be finite and positive");
        }
    }

    private static double clamp(double minimum, double value, double maximum) {
        return Math.clamp(value, minimum, maximum);
    }

    enum Surface {
        CHOICE(480.0, 0.40, 768.0, 0.75),
        CONFIRMATION(480.0, 0.40, 768.0, 0.75),
        FREE_TEXT(760.0, 0.55, 1056.0, 0.88);

        private final double minimumWidth;
        private final double preferredWidthRatio;
        private final double maximumWidth;
        private final double maximumHeightRatio;

        Surface(double minimumWidth, double preferredWidthRatio,
                double maximumWidth, double maximumHeightRatio) {
            this.minimumWidth = minimumWidth;
            this.preferredWidthRatio = preferredWidthRatio;
            this.maximumWidth = maximumWidth;
            this.maximumHeightRatio = maximumHeightRatio;
        }
    }

    record Layout(Rectangle2D stageBounds, double panelWidth,
                  double panelHeight, boolean compact) {
    }

    private record Width(double stageWidth, double panelWidth, double margin) {
    }
}

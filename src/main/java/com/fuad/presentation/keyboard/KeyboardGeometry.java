package com.fuad.presentation.keyboard;

import javafx.geometry.Point2D;
import javafx.scene.shape.ArcTo;
import javafx.scene.shape.ClosePath;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.QuadCurveTo;

public final class KeyboardGeometry {
    private KeyboardGeometry() {
        /* Empty intentionally */
    }

    public static Point2D polar(double cx, double cy, double radius, double angleDeg) {
        requireFinite(cx, cy, radius, angleDeg);
        double angle = Math.toRadians(angleDeg);
        return new Point2D(cx + radius * Math.cos(angle), cy + radius * Math.sin(angle));
    }

    /**
     * A clockwise ring segment with small rounded corners, in screen coordinates.
     */
    public static Path annularKey(double cx, double cy, double innerRadius, double outerRadius, double startDeg, double endDeg) {
        requireFinite(cx, cy, innerRadius, outerRadius, startDeg, endDeg);
        if (innerRadius <= 0 || outerRadius <= innerRadius || endDeg <= startDeg || endDeg - startDeg >= 180) {
            throw new IllegalArgumentException("Invalid radial key geometry");
        }
        double corner = Math.min(4, Math.min((outerRadius - innerRadius) / 4,
                innerRadius * Math.toRadians(endDeg - startDeg) / 4));
        double outerAngle = Math.toDegrees(corner / outerRadius);
        double innerAngle = Math.toDegrees(corner / innerRadius);
        Point2D start = polar(cx, cy, outerRadius, startDeg + outerAngle);
        Path path = new Path(new MoveTo(start.getX(), start.getY()));

        arc(path, outerRadius, polar(cx, cy, outerRadius, endDeg - outerAngle), true);
        quad(path, polar(cx, cy, outerRadius, endDeg), polar(cx, cy, outerRadius - corner, endDeg));
        line(path, polar(cx, cy, innerRadius + corner, endDeg));
        quad(path, polar(cx, cy, innerRadius, endDeg), polar(cx, cy, innerRadius, endDeg - innerAngle));
        arc(path, innerRadius, polar(cx, cy, innerRadius, startDeg + innerAngle), false);
        quad(path, polar(cx, cy, innerRadius, startDeg), polar(cx, cy, innerRadius + corner, startDeg));
        line(path, polar(cx, cy, outerRadius - corner, startDeg));
        quad(path, polar(cx, cy, outerRadius, startDeg), start);
        path.getElements().add(new ClosePath());
        return path;
    }

    /**
     * A rounded key whose top and bottom follow the same exact parabola.
     */
    public static Path curvedKey(double x, double y, double width, double height, double rowLeft, double rowWidth, double bow) {
        return warpedKey(x, y, width, height, rowLeft, rowWidth, bow, 0, 0);
    }

    /**
     * A rounded panel with independently bowed horizontal edges and sloping sides.
     * Both parabolas use the full panel width: positive bow lowers their ends.
     * The lower corners move inward by {@code inset}; negative values flare outward.
     */
    public static Path bowedPanel(double x, double y, double width, double height,
                                  double topBow, double bottomBow, double inset, double corner) {
        requireFinite(x, y, width, height, topBow, bottomBow, inset, corner);
        double lowerWidth = width - 2 * inset;
        if (width <= 0 || height <= 0 || lowerWidth <= 0 || corner < 0) {
            throw new IllegalArgumentException("Invalid bowed panel geometry");
        }
        Point2D topLeft = bowedPoint(x, y, x, width, topBow);
        Point2D topRight = bowedPoint(x + width, y, x, width, topBow);
        Point2D bottomLeft = bowedPoint(x + inset, y + height, x, width, bottomBow);
        Point2D bottomRight = bowedPoint(x + width - inset, y + height, x, width, bottomBow);
        if (bottomLeft.getY() <= topLeft.getY() || bottomRight.getY() <= topRight.getY()) {
            throw new IllegalArgumentException("Bowed panel edges must not cross");
        }
        double radius = Math.min(corner, Math.min(Math.min(width, lowerWidth) / 4,
                Math.min(topLeft.distance(bottomLeft), topRight.distance(bottomRight)) / 4));
        Point2D rightTrim = bottomRight.subtract(topRight).normalize().multiply(radius);
        Point2D leftTrim = bottomLeft.subtract(topLeft).normalize().multiply(radius);
        Point2D start = bowedPoint(x + radius, y, x, width, topBow);
        Path path = new Path(new MoveTo(start.getX(), start.getY()));
        bowedHorizontal(path, x + radius, x + width - radius, y, x, width, topBow);
        quad(path, topRight, topRight.add(rightTrim));
        line(path, bottomRight.subtract(rightTrim));
        quad(path, bottomRight,
                bowedPoint(x + width - inset - radius, y + height, x, width, bottomBow));
        bowedHorizontal(path, x + width - inset - radius, x + inset + radius,
                y + height, x, width, bottomBow);
        quad(path, bottomLeft, bottomLeft.subtract(leftTrim));
        line(path, topLeft.add(leftTrim));
        quad(path, topLeft, start);
        path.getElements().add(new ClosePath());
        return path;
    }

    /**
     * Applies the row's bow, slope and horizontal shear to a rounded key.
     */
    public static Path warpedKey(double x, double y, double width, double height, double rowLeft,
                                 double rowWidth, double bow, double slope, double shear) {
        requireFinite(x, y, width, height, rowLeft, rowWidth, bow, slope, shear);
        if (width <= 0 || height <= 0 || rowWidth <= 0) {
            throw new IllegalArgumentException("Invalid curved key geometry");
        }
        double corner = Math.min(14, Math.min(width, height) / 4);
        double right = x + width;
        double bottom = y + height;
        Path path = new Path();
        Point2D start = warpedPoint(x + corner, y, rowLeft, rowWidth, bow, slope, shear);
        path.getElements().add(new MoveTo(start.getX(), start.getY()));
        warpedHorizontal(path, x + corner, right - corner, y, rowLeft, rowWidth, bow, slope, shear);
        quad(path, warpedPoint(right, y, rowLeft, rowWidth, bow, slope, shear),
                warpedPoint(right, y + corner, rowLeft, rowWidth, bow, slope, shear));
        line(path, warpedPoint(right, bottom - corner, rowLeft, rowWidth, bow, slope, shear));
        quad(path, warpedPoint(right, bottom, rowLeft, rowWidth, bow, slope, shear),
                warpedPoint(right - corner, bottom, rowLeft, rowWidth, bow, slope, shear));
        warpedHorizontal(path, right - corner, x + corner, bottom, rowLeft, rowWidth, bow, slope, shear);
        quad(path, warpedPoint(x, bottom, rowLeft, rowWidth, bow, slope, shear),
                warpedPoint(x, bottom - corner, rowLeft, rowWidth, bow, slope, shear));
        line(path, warpedPoint(x, y + corner, rowLeft, rowWidth, bow, slope, shear));
        quad(path, warpedPoint(x, y, rowLeft, rowWidth, bow, slope, shear), start);
        path.getElements().add(new ClosePath());
        return path;
    }

    /**
     * Uses the same transform as warpedKey so labels remain centered on their keys.
     */
    public static Point2D warpedPoint(double x, double y, double rowLeft, double rowWidth,
                                      double bow, double slope, double shear) {
        requireFinite(x, y, rowLeft, rowWidth, bow, slope, shear);
        return new Point2D(x + shear * y, y + curveY(x, rowLeft, rowWidth, bow) + slope * (x - rowLeft));
    }

    public static double curveY(double x, double rowLeft, double rowWidth, double bow) {
        requireFinite(x, rowLeft, rowWidth, bow);
        if (rowWidth <= 0) {
            throw new IllegalArgumentException("Row width must be positive");
        }
        double center = rowLeft + rowWidth / 2.0;
        double normalized = (x - center) / (rowWidth / 2.0);
        return bow * normalized * normalized;
    }

    private static void warpedHorizontal(Path path, double x1, double x2, double y, double rowLeft,
                                         double rowWidth, double bow, double slope, double shear) {
        Point2D start = warpedPoint(x1, y, rowLeft, rowWidth, bow, slope, shear);
        Point2D middle = warpedPoint((x1 + x2) / 2, y, rowLeft, rowWidth, bow, slope, shear);
        Point2D end = warpedPoint(x2, y, rowLeft, rowWidth, bow, slope, shear);
        // A quadratic Bezier passes through its curve midpoint at t = 1/2.
        Point2D control = middle.multiply(2).subtract(start.midpoint(end));
        quad(path, control, end);
    }

    private static Point2D bowedPoint(double x, double y, double rowLeft, double rowWidth, double bow) {
        return new Point2D(x, y + curveY(x, rowLeft, rowWidth, bow));
    }

    private static void bowedHorizontal(Path path, double x1, double x2, double y,
                                        double rowLeft, double rowWidth, double bow) {
        Point2D start = bowedPoint(x1, y, rowLeft, rowWidth, bow);
        Point2D middle = bowedPoint((x1 + x2) / 2, y, rowLeft, rowWidth, bow);
        Point2D end = bowedPoint(x2, y, rowLeft, rowWidth, bow);
        quad(path, middle.multiply(2).subtract(start.midpoint(end)), end);
    }

    private static void arc(Path path, double radius, Point2D end, boolean clockwise) {
        path.getElements().add(new ArcTo(radius, radius, 0, end.getX(), end.getY(), false, clockwise));
    }

    private static void line(Path path, Point2D end) {
        path.getElements().add(new LineTo(end.getX(), end.getY()));
    }

    private static void quad(Path path, Point2D control, Point2D end) {
        path.getElements().add(new QuadCurveTo(control.getX(), control.getY(), end.getX(), end.getY()));
    }

    private static void requireFinite(double... values) {
        for (double value : values) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("Keyboard coordinates must be finite");
            }
        }
    }
}

package com.fuad.view.icon;

import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.*;

import java.util.Objects;

public class HudIconView extends StackPane {
    public static final double DESIGN_SIZE = 24.0;

    public HudIconView(HudIcon icon, double size) {
        Group glyph;
        double scale;

        Objects.requireNonNull(icon, "icon");
        if (size <= 0.0) {
            throw new IllegalArgumentException("Size must be greater than 0.");
        }
        glyph = createGlyph(icon);
        scale = size / DESIGN_SIZE;
        glyph.setScaleX(scale);
        glyph.setScaleY(scale);

        setAlignment(Pos.CENTER);

        setMinSize(size, size);
        setPrefSize(size, size);
        setMaxSize(size, size);

        setMouseTransparent(true);
        setPickOnBounds(false);

        getStyleClass().add("hud-icon");

        getChildren().add(glyph);
    }

    private static Group createGlyph(HudIcon icon) {
        return switch (icon) {
            case PLAY -> group(fill(new Polygon(7.0, 5.0, 19.0, 12.0, 7.0, 19.0)));
            case PAUSE ->group(fill(new Rectangle(6.0, 5.0, 4.0, 14.0)), fill(new Rectangle(14.0, 5.0, 4.0, 14.0)));
            case PREVIOUS -> group(stroke(new Line(6.0, 5.0, 6.0, 19.0)), fill(new Polygon(18.0, 5.0, 8.0, 12.0, 18.0, 19.0)));
            case NEXT -> group(stroke(
                    new Line(18.0, 5.0, 18.0, 19.0)), fill(new Polygon(6.0, 5.0, 16.0, 12.0, 6.0, 19.0)));
            case BACK -> group(stroke(new Polyline(14.0, 5.0, 7.0, 12.0, 14.0, 19.0)),
                    stroke(new Line(7.0, 12.0, 20.0, 12.0)));
            case UP -> group(stroke(new Polyline(5.0, 14.0, 12.0, 7.0, 19.0, 14.0)),
                    stroke(new Line(12.0, 7.0, 12.0, 20.0)));
            case HOME -> group(stroke(new Polyline(4.0, 11.0, 12.0, 4.0, 20.0, 11.0)),
                    stroke(new Rectangle(6.0, 10.0, 12.0, 10.0)));
            case SEARCH -> group(stroke(new Circle(10.0, 10.0, 6.0)),
                    stroke(new Line(14.5, 14.5, 20.0, 20.0)));
            case AUDIO -> group(fill(new Polygon(4.0, 10.0, 8.0, 10.0, 13.0, 6.0, 13.0, 18.0, 8.0, 14.0, 4.0, 14.0)),
                    stroke(arc(13.0, 12.0, 5.0, -55.0, 110.0)), stroke(arc(13.0, 12.0, 8.0, -50.0, 100.0)));
            case NETWORK -> group(stroke(arc(12.0, 14.0, 9.0, 35.0, 110.0)), stroke(arc(12.0, 14.0, 6.0, 35.0, 110.0)),
                    fill(new Circle(12.0, 17.0, 1.5)));
            case DISPLAY -> group(stroke(new Rectangle(
                    3.0, 4.0, 18.0, 13.0)), stroke(new Line(12.0, 17.0, 12.0, 21.0)),
                    stroke(new Line(8.0, 21.0, 16.0, 21.0)));
            case POWER -> group(stroke(arc(12.0, 12.0, 8.0, -55.0, 290.0)),
                    stroke(new Line(12.0, 3.0, 12.0, 11.0)));
            case FOLDER -> group(stroke(new Path(new MoveTo(3.0, 7.0), new LineTo(9.0, 7.0), new LineTo(11.0, 9.0),
                    new LineTo(21.0, 9.0), new LineTo(21.0, 19.0), new LineTo(3.0, 19.0), new ClosePath()))
            );
            case GLOBE -> group(stroke(new Circle(12.0, 12.0, 9.0)), stroke(new Line(3.0, 12.0, 21.0, 12.0)),
                    stroke(new Ellipse(12.0, 12.0, 4.0, 9.0)));
            case TERMINAL -> group(stroke(new Polyline(4.0, 6.0, 10.0, 12.0, 4.0, 18.0)),
                    stroke(new Line(12.0, 18.0, 20.0, 18.0)));
            case MORE -> group(fill(new Circle(6.0, 12.0, 1.5)), fill(new Circle(12.0, 12.0, 1.5)),
                    fill(new Circle(18.0, 12.0, 1.5)));
        };
    }

    private static Group group(Node... node) {
        return new Group(node);
    }

    private static <T extends Shape> T stroke(T shape) {
        shape.getStyleClass().add("hud-icon-stroke");
        return shape;
    }

    private static <T extends Shape> T fill(T shape) {
        shape.getStyleClass().add("hud-icon-fill");
        return shape;
    }

    private static Arc arc(double centerX, double centerY, double radius, double startAngle, double length) {
        Arc arc = new Arc(centerX, centerY, radius, radius, startAngle, length);

        arc.setType(ArcType.OPEN);
        return arc;
    }
}

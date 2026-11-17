package com.fuad.presentation.keyboard;

import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.transform.Scale;
import javafx.scene.transform.Translate;

/** Shared, font-independent line icons for the command surface. */
final class AresKeyboardIcons {
    private AresKeyboardIcons() {
    }

    /** Returns an icon centered at the origin, scaled from a 24-unit drawing. */
    static SVGPath icon(String name, double size, Color color) {
        if (!Double.isFinite(size) || size <= 0 || color == null) {
            throw new IllegalArgumentException("An icon requires a positive size and a color");
        }
        SVGPath icon = new SVGPath();
        icon.setContent(switch (name) {
            case "QUERY", "SEARCH" -> "M5,-3 A7,7 0 1,1 -9,-3 A7,7 0 1,1 5,-3 M3,3 L11,11";
            case "SPEAKER", "UNMUTE" -> "M-11,-4 H-6 L0,-10 V10 L-6,4 H-11 Z M4,-5 Q9,0 4,5 M7,-9 Q15,0 7,9";
            case "DECK" -> "M0,-11 L11,-5 L0,1 L-11,-5 Z M-9,1 L0,6 L9,1 M-9,6 L0,11 L9,6";
            case "WEATHER", "CLIMA" -> "M-7,9 H7 A5,5 0 0,0 8,-1 A8,8 0 0,0 -7,-3 A6,6 0 0,0 -7,9 M4,-7 A4,4 0 0,1 11,-3 M8,-12 V-10 M14,-9 L12,-7 M16,-3 H14 M1,-11 L3,-9";
            case "MEDIA", "EXECUTE", "PLAY" -> "M-7,-10 L10,0 L-7,10 Z";
            case "FILES", "FILE" -> "M-11,-7 V9 H11 V-6 H0 L-3,-10 H-11 V-7 H11";
            case "SYSTEM" -> "M-4,-11 H4 L5,-7 L8,-5 L11,-5 L13,0 L10,3 L9,6 L9,9 L4,12 L1,9 H-2 L-5,11 L-10,8 L-9,4 L-11,1 L-13,-1 L-10,-6 L-6,-6 Z M4,0 A4,4 0 1,1 -4,0 A4,4 0 1,1 4,0";
            case "WEB" -> "M11,0 A11,11 0 1,1 -11,0 A11,11 0 1,1 11,0 M-11,0 H11 M-9,-6 H9 M-9,6 H9 M0,-11 C-7,-6 -7,6 0,11 C7,6 7,-6 0,-11";
            case "MUSIC", "DJ" -> "M-3,6 V-8 L10,-11 V3 M-3,-4 L10,-7 M-3,6 C-4,1 -12,3 -11,7 C-10,11 -3,10 -3,6 M10,3 C9,-2 1,0 2,4 C3,8 10,7 10,3";
            case "APPS", "WINDOWS" -> "M-10,-10 H-2 V-2 H-10 Z M2,-10 H10 V-2 H2 Z M-10,2 H-2 V10 H-10 Z M2,2 H10 V10 H2 Z";
            case "UP" -> "M-8,5 L0,-5 L8,5";
            case "DOWN" -> "M-8,-5 L0,5 L8,-5";
            case "LEFT" -> "M5,-8 L-5,0 L5,8";
            case "RIGHT", "CHEVRON" -> "M-5,-8 L5,0 L-5,8";
            case "BACK" -> "M-5,-9 L-11,-3 L-5,3 M-10,-3 H2 A8,8 0 1,1 2,13 H-4";
            case "HOME" -> "M-12,-1 L0,-11 L12,-1 M-9,-3 V10 H-3 V3 H3 V10 H9 V-3";
            case "FOCUS" -> "M-4,-10 H-10 V-4 M4,-10 H10 V-4 M10,4 V10 H4 M-4,10 H-10 V4";
            case "MIC" -> "M-4,-6 V1 A4,4 0 0,0 4,1 V-6 A4,4 0 0,0 -4,-6 M-8,-1 V1 A8,8 0 0,0 8,1 V-1 M0,9 V13 M-4,13 H4";
            case "MUTED" -> "M-4,-6 V-4 M4,1 V-6 A4,4 0 0,0 -4,-6 M-8,-1 V1 A8,8 0 0,0 5,7 M8,-1 V1 M0,9 V13 M-4,13 H4 M-11,-11 L11,13";
            case "MODEL", "MCP", "MCP STUB" -> "M0,-12 L11,-6 V6 L0,12 L-11,6 V-6 Z M-11,-6 L0,0 L11,-6 M0,0 V12";
            case "PAUSE" -> "M-5,-9 V9 M5,-9 V9";
            case "STOP" -> "M-8,-8 H8 V8 H-8 Z";
            case "DOWNLOAD" -> "M0,-11 V4 M-6,-2 L0,4 L6,-2 M-10,3 V10 H10 V3";
            case "MORE" -> "M0,-9 A1,1 0 1,1 0,-7 A1,1 0 1,1 0,-9 M0,-1 A1,1 0 1,1 0,1 A1,1 0 1,1 0,-1 M0,7 A1,1 0 1,1 0,9 A1,1 0 1,1 0,7";
            case "MANAGE" -> "M-10,-7 H10 M-10,0 H10 M-10,7 H10 M-5,-10 V-4 M5,-3 V3 M-2,4 V10";
            case "MEMORY" -> "M-8,-8 H8 V8 H-8 Z M-4,-4 H4 V4 H-4 Z M-5,-12 V-8 M0,-12 V-8 M5,-12 V-8 M-5,8 V12 M0,8 V12 M5,8 V12 M-12,-5 H-8 M-12,0 H-8 M-12,5 H-8 M8,-5 H12 M8,0 H12 M8,5 H12";
            case "LAYERS" -> "M0,-11 L12,-5 L0,1 L-12,-5 Z M-12,1 L0,7 L12,1 M-12,7 L0,13 L12,7";
            case "TERMINAL" -> "M-10,-7 L-2,0 L-10,7 M2,8 H11";
            case "INFO" -> "M11,0 A11,11 0 1,1 -11,0 A11,11 0 1,1 11,0 M0,-1 V7 M0,-7 V-6";
            case "CLOSE" -> "M-7,-7 L7,7 M7,-7 L-7,7";
            default -> throw new IllegalArgumentException("Unknown keyboard icon: " + name);
        });
        icon.setFill(null);
        icon.setStroke(color);
        icon.setStrokeWidth(1.65);
        icon.setStrokeLineCap(StrokeLineCap.ROUND);
        icon.setStrokeLineJoin(StrokeLineJoin.ROUND);
        double factor = size / 24;
        var bounds = icon.getLayoutBounds();
        icon.getTransforms().addAll(new Scale(factor, factor, 0, 0),
                new Translate(-(bounds.getMinX() + bounds.getMaxX()) / 2,
                        -(bounds.getMinY() + bounds.getMaxY()) / 2));
        icon.setMouseTransparent(true);
        return icon;
    }
}

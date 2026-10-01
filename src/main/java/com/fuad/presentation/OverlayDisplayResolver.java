package com.fuad.presentation;

import com.fuad.presentation.interaction.InteractionDisplayResolver;
import com.fuad.presentation.interaction.ResolvedInteractionDisplay;
import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class OverlayDisplayResolver {
    private final InteractionDisplayResolver commandDeckResolver;
    private final WindowsOverlayOwnerSupport windowSupport;

    public OverlayDisplayResolver(InteractionDisplayResolver commandDeckResolver, WindowsOverlayOwnerSupport windowSupport) {
        this.commandDeckResolver = Objects.requireNonNull(commandDeckResolver);
        this.windowSupport = Objects.requireNonNull(windowSupport);
    }

    public Screen resolve() {
        Screen reserved = commandDeckResolver.resolve().map(ResolvedInteractionDisplay::screen).orElse(null);
        Rectangle2D foregroundBounds = windowSupport.foregroundWindowBounds();

        if (foregroundBounds != null) {
            Screen focusedScreen = resolveByOverlap(foregroundBounds, Screen.getScreens());
            if (focusedScreen != null && !sameScreen(focusedScreen, reserved)) {
                return focusedScreen;
            }
        }
        return fallback(reserved);
    }

    private static Screen resolveByOverlap(Rectangle2D window, List<Screen> screens) {
        return screens.stream().map(screen -> new Candidate(screen, overlap(window, screen.getBounds())))
                .filter(candidate -> candidate.overlap() > 0.0).max(Comparator.comparingDouble(Candidate::overlap))
                .map(Candidate::screen).orElse(null);
    }

    private static Screen fallback(Screen reserved) {
        Screen primary = Screen.getPrimary();

        if (!sameScreen(primary, reserved)) {
            return primary;
        }
        return Screen.getScreens().stream().filter(screen -> !sameScreen(screen, reserved)).findFirst().orElse(primary);
    }

    private static boolean sameScreen(Screen left, Screen right) {
        if (left == null || right == null) {
            return false;
        }
        return left.getBounds().equals(right.getBounds());
    }

    private static double overlap(Rectangle2D left, Rectangle2D right) {
        double width = Math.max(0.0, Math.min(left.getMaxX(), right.getMaxX()) - Math.max(left.getMinX(), right.getMinX()));
        double height = Math.max(0.0, Math.min(left.getMaxX(), right.getMaxY()) - Math.max(left.getMinY(), right.getMinY()));

        return width * height;
    }

    private record Candidate(Screen screen, double overlap) {
        /* Empty intentionally */
    }
}

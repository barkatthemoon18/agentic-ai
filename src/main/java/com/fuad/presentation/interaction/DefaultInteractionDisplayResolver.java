package com.fuad.presentation.interaction;

import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class DefaultInteractionDisplayResolver implements InteractionDisplayResolver {
    private final InteractionDisplayConfig config;
    private final NativeDisplayDiscovery discovery;
    private final Supplier<List<Screen>> screens;
    private final Consumer<String> diagnostics;

    public static DefaultInteractionDisplayResolver platformDefault(Path configPath) {
        InteractionDisplayConfig config = new InteractionDisplayConfigLoader().load(configPath);
        NativeDisplayDiscovery discovery = System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT).startsWith("windows")
                ? new WindowsNativeDisplayDiscovery() : List::of;
        return new DefaultInteractionDisplayResolver(config, discovery,
                () -> List.copyOf(Screen.getScreens()),
                message -> System.out.println("Interaction display: " + message));
    }

    DefaultInteractionDisplayResolver(InteractionDisplayConfig config,
                                      NativeDisplayDiscovery discovery,
                                      Supplier<List<Screen>> screens,
                                      Consumer<String> diagnostics) {
        this.config = Objects.requireNonNull(config);
        this.discovery = Objects.requireNonNull(discovery);
        this.screens = Objects.requireNonNull(screens);
        this.diagnostics = Objects.requireNonNull(diagnostics);
    }

    @Override
    public Optional<ResolvedInteractionDisplay> resolve() {
        List<Screen> fxScreens = screens.get();
        if (fxScreens.isEmpty()) {
            diagnostics.accept("no JavaFX screens available");
            return Optional.empty();
        }
        List<NativeDisplay> nativeDisplays;
        try {
            nativeDisplays = discovery.discover();
        }
        catch (RuntimeException | LinkageError e) {
            diagnostics.accept("native discovery unavailable: " + safeMessage(e));
            nativeDisplays = List.of();
        }
        if (nativeDisplays.isEmpty()) {
            nativeDisplays = fromJavaFx(fxScreens);
        }
        nativeDisplays.forEach(display -> diagnostics.accept(String.format(
                "%s [%s] %dx%d at %d,%d%s", display.id(), display.nativeName(),
                display.width(), display.height(), display.x(), display.y(),
                display.primary() ? " primary" : "")));

        Selection selection = select(nativeDisplays).orElse(null);
        if (selection == null) {
            return Optional.empty();
        }
        NativeDisplay selected = selection.display();
        Screen screen = mapToJavaFx(selected, fxScreens).orElse(null);
        if (screen == null) {
            diagnostics.accept("unable to map native display " + selected.id() + " to JavaFX");
            return Optional.empty();
        }
        diagnostics.accept("selected " + selected.id() + " by " + selection.reason());
        return Optional.of(new ResolvedInteractionDisplay(selected.id(), screen, selection.reason()));
    }

    Optional<Selection> select(List<NativeDisplay> displays) {
        Optional<NativeDisplay> configured = configured(displays);
        if (configured.isPresent()) {
            NativeDisplay selected = configured.get();
            if (!selected.primary() || config.allowPrimary()) {
                return Optional.of(new Selection(selected, "configured identity"));
            }
            diagnostics.accept("configured display is primary and allowPrimary is false");
        }

        List<NativeDisplay> resolutionMatches = displays.stream()
                .filter(display -> config.allowPrimary() || !display.primary())
                .filter(display -> display.width() == config.fallback().width()
                        && display.height() == config.fallback().height())
                .toList();
        if (resolutionMatches.size() == 1) {
            NativeDisplay selected = resolutionMatches.getFirst();
            return Optional.of(new Selection(selected,
                    "unique " + selected.width() + "x" + selected.height() + " fallback"));
        }
        if (resolutionMatches.size() > 1) {
            diagnostics.accept("fallback resolution is ambiguous, found "
                    + resolutionMatches.size() + " matches");
            return Optional.empty();
        }

        Comparator<NativeDisplay> stableOrder = Comparator
                .comparing(NativeDisplay::nativeName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(NativeDisplay::id, String.CASE_INSENSITIVE_ORDER);
        Optional<NativeDisplay> secondary = displays.stream()
                .filter(display -> !display.primary())
                .min(stableOrder);
        if (secondary.isPresent()) {
            return Optional.of(new Selection(secondary.get(), "available secondary display"));
        }
        Optional<NativeDisplay> primary = displays.stream()
                .filter(NativeDisplay::primary)
                .min(stableOrder);
        if (primary.isPresent()) {
            return Optional.of(new Selection(primary.get(), "primary display as final fallback"));
        }
        diagnostics.accept("no display is available for the interaction surface");
        return Optional.empty();
    }

    private Optional<NativeDisplay> configured(List<NativeDisplay> displays) {
        if (config.displayId().isBlank()) {
            return Optional.empty();
        }
        List<NativeDisplay> matches = displays.stream()
                .filter(display -> display.id().equalsIgnoreCase(config.displayId())
                        || display.nativeName().equalsIgnoreCase(config.displayId()))
                .toList();
        return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
    }

    private Optional<Screen> mapToJavaFx(NativeDisplay display, List<Screen> fxScreens) {
        Rectangle2D nativeBounds = new Rectangle2D(display.x(), display.y(),
                display.width(), display.height());
        return fxScreens.stream()
                .map(screen -> new ScreenScore(screen, overlap(nativeBounds, screen.getBounds())))
                .filter(score -> score.overlap > 0)
                .max(Comparator.comparingDouble(ScreenScore::overlap))
                .map(ScreenScore::screen);
    }

    private static double overlap(Rectangle2D left, Rectangle2D right) {
        double width = Math.max(0, Math.min(left.getMaxX(), right.getMaxX())
                - Math.max(left.getMinX(), right.getMinX()));
        double height = Math.max(0, Math.min(left.getMaxY(), right.getMaxY())
                - Math.max(left.getMinY(), right.getMinY()));
        return width * height;
    }

    private static List<NativeDisplay> fromJavaFx(List<Screen> screens) {
        List<NativeDisplay> result = new ArrayList<>();
        Screen primary = Screen.getPrimary();
        for (Screen screen : screens) {
            Rectangle2D bounds = screen.getBounds();
            String id = String.format(Locale.ROOT, "javafx:%.0f:%.0f:%.0fx%.0f",
                    bounds.getMinX(), bounds.getMinY(), bounds.getWidth(), bounds.getHeight());
            result.add(new NativeDisplay(id, id, (int) bounds.getMinX(),
                    (int) bounds.getMinY(), (int) bounds.getWidth(),
                    (int) bounds.getHeight(), screen.equals(primary)));
        }
        return result;
    }

    private static String safeMessage(Throwable failure) {
        return failure.getMessage() == null
                ? failure.getClass().getSimpleName() : failure.getMessage();
    }

    private record ScreenScore(Screen screen, double overlap) {
    }

    record Selection(NativeDisplay display, String reason) {
    }
}

package com.fuad.presentation.interaction;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DefaultInteractionDisplayResolverTest {
    @Test
    void configuredIdentityShouldWin() {
        List<String> diagnostics = new ArrayList<>();
        DefaultInteractionDisplayResolver resolver = new DefaultInteractionDisplayResolver(
                new InteractionDisplayConfig("persistent-touch",
                        new InteractionDisplayConfig.Fallback(1920, 1080), false),
                () -> List.of(
                        new NativeDisplay("primary", "\\\\.\\DISPLAY1",
                                0, 0, 2560, 1440, true),
                        new NativeDisplay("persistent-touch", "\\\\.\\DISPLAY2",
                                2560, 0, 1920, 1080, false)),
                List::of, diagnostics::add);

        DefaultInteractionDisplayResolver.Selection selection = resolver.select(List.of(
                new NativeDisplay("primary", "\\\\.\\DISPLAY1",
                        0, 0, 2560, 1440, true),
                new NativeDisplay("persistent-touch", "\\\\.\\DISPLAY2",
                        2560, 0, 1920, 1080, false))).orElseThrow();

        assertEquals("persistent-touch", selection.display().id());
        assertTrue(selection.reason().contains("configured"));
    }

    @Test
    void ambiguousResolutionFallbackShouldRemainUnavailable() {
        DefaultInteractionDisplayResolver resolver = new DefaultInteractionDisplayResolver(
                InteractionDisplayConfig.defaults(),
                () -> List.of(
                        new NativeDisplay("one", "one", 0, 0, 1920, 1080, false),
                        new NativeDisplay("two", "two", 1920, 0, 1920, 1080, false)),
                List::of, ignored -> { });

        assertTrue(resolver.select(List.of(
                new NativeDisplay("one", "one", 0, 0, 1920, 1080, false),
                new NativeDisplay("two", "two", 1920, 0, 1920, 1080, false))).isEmpty());
    }

    @Test
    void uniqueResolutionMatchShouldWinBeforeAvailableDisplayFallback() {
        DefaultInteractionDisplayResolver resolver = resolver(InteractionDisplayConfig.defaults());

        DefaultInteractionDisplayResolver.Selection selection = resolver.select(List.of(
                new NativeDisplay("primary", "\\\\.\\DISPLAY2",
                        0, 0, 2560, 1440, true),
                new NativeDisplay("touch", "\\\\.\\DISPLAY3",
                        2560, 0, 1920, 1080, false),
                new NativeDisplay("secondary", "\\\\.\\DISPLAY1",
                        -2560, 0, 2560, 1440, false))).orElseThrow();

        assertEquals("touch", selection.display().id());
        assertTrue(selection.reason().contains("1920x1080"));
    }

    @Test
    void shouldUseAvailableSecondaryWhenResolutionDoesNotMatch() {
        DefaultInteractionDisplayResolver resolver = resolver(InteractionDisplayConfig.defaults());

        DefaultInteractionDisplayResolver.Selection selection = resolver.select(List.of(
                new NativeDisplay("primary", "\\\\.\\DISPLAY2",
                        0, 0, 2560, 1440, true),
                new NativeDisplay("secondary", "\\\\.\\DISPLAY1",
                        -2560, 0, 2560, 1440, false))).orElseThrow();

        assertEquals("secondary", selection.display().id());
        assertEquals("available secondary display", selection.reason());
    }

    @Test
    void availableSecondarySelectionShouldBeDeterministic() {
        DefaultInteractionDisplayResolver resolver = resolver(InteractionDisplayConfig.defaults());

        DefaultInteractionDisplayResolver.Selection selection = resolver.select(List.of(
                new NativeDisplay("right", "\\\\.\\DISPLAY3",
                        2560, 0, 2560, 1440, false),
                new NativeDisplay("primary", "\\\\.\\DISPLAY2",
                        0, 0, 2560, 1440, true),
                new NativeDisplay("left", "\\\\.\\DISPLAY1",
                        -2560, 0, 2560, 1440, false))).orElseThrow();

        assertEquals("left", selection.display().id());
    }

    @Test
    void shouldUsePrimaryAsFinalFallbackWhenNoSecondaryExists() {
        DefaultInteractionDisplayResolver primaryOnly = resolver(InteractionDisplayConfig.defaults());

        DefaultInteractionDisplayResolver.Selection selection = primaryOnly.select(List.of(
                new NativeDisplay("primary", "primary",
                        0, 0, 2560, 1440, true))).orElseThrow();

        assertEquals("primary", selection.display().id());
        assertEquals("primary display as final fallback", selection.reason());
    }

    @Test
    void configuredDisallowedPrimaryShouldFallBackToSecondary() {
        DefaultInteractionDisplayResolver resolver = resolver(new InteractionDisplayConfig(
                "primary", new InteractionDisplayConfig.Fallback(1920, 1080), false));

        DefaultInteractionDisplayResolver.Selection selection = resolver.select(List.of(
                new NativeDisplay("primary", "\\\\.\\DISPLAY2",
                        0, 0, 2560, 1440, true),
                new NativeDisplay("secondary", "\\\\.\\DISPLAY1",
                        -2560, 0, 2560, 1440, false))).orElseThrow();

        assertEquals("secondary", selection.display().id());
    }

    private DefaultInteractionDisplayResolver resolver(InteractionDisplayConfig config) {
        return new DefaultInteractionDisplayResolver(config, List::of, List::of, ignored -> { });
    }
}

package com.fuad.presentation.interaction;

import javafx.geometry.Rectangle2D;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InteractionGeometryTest {

    @Test
    void shouldAnchorChoiceAtBottomRightInLogicalCoordinates() {
        Rectangle2D visualBounds = new Rectangle2D(0, 0, 1920, 1080);

        InteractionGeometry.Layout layout = InteractionGeometry.calculate(visualBounds,
                InteractionGeometry.Surface.CHOICE, 400.0);

        assertEquals(new Rectangle2D(1144, 624, 768, 448), layout.stageBounds());
        assertEquals(720, layout.panelWidth());
        assertEquals(400, layout.panelHeight());
        assertFalse(layout.compact());
    }

    @Test
    void naturalHeightShouldDescribePanelWithoutHaloOrOuterMargin() {
        InteractionGeometry.Layout layout = InteractionGeometry.calculate(
                new Rectangle2D(0, 0, 1920, 1080),
                InteractionGeometry.Surface.CONFIRMATION, 312.0);

        assertEquals(312.0 + 2.0 * InteractionGeometry.HALO_PADDING,
                layout.stageBounds().getHeight());
        assertEquals(312.0, layout.panelHeight());
    }

    @Test
    void shouldCapChoiceAtSeventyFivePercent() {
        InteractionGeometry.Layout layout = InteractionGeometry.calculate(
                new Rectangle2D(0, 0, 1920, 1080),
                InteractionGeometry.Surface.CHOICE, 2000.0);

        assertEquals(810.0, layout.stageBounds().getHeight());
        assertEquals(762.0, layout.panelHeight());
    }

    @Test
    void shouldCapFreeTextAtEightyEightPercent() {
        InteractionGeometry.Layout layout = InteractionGeometry.calculate(
                new Rectangle2D(0, 0, 1920, 1080),
                InteractionGeometry.Surface.FREE_TEXT, 2000.0);

        assertEquals(950.4, layout.stageBounds().getHeight(), 0.0001);
        assertEquals(902.4, layout.panelHeight(), 0.0001);
    }

    @Test
    void shouldUseCompactFreeTextOnLowLogicalDisplay() {
        Rectangle2D display = new Rectangle2D(0, 0, 1280, 799);

        assertTrue(InteractionGeometry.calculate(display,
                InteractionGeometry.Surface.FREE_TEXT, 500).compact());
        assertFalse(InteractionGeometry.calculate(new Rectangle2D(0, 0, 1280, 800),
                InteractionGeometry.Surface.FREE_TEXT, 500).compact());
    }

    @Test
    void shouldKeepEntireStageInsideNegativeVisualBounds() {
        Rectangle2D visualBounds = new Rectangle2D(-2560, -240, 1280, 720);

        InteractionGeometry.Layout layout = InteractionGeometry.calculate(visualBounds,
                InteractionGeometry.Surface.CHOICE, 1500.0);

        assertContained(visualBounds, layout.stageBounds());
        assertEquals(540.0, layout.stageBounds().getHeight());
        assertEquals(512.0, layout.stageBounds().getWidth());
    }

    @ParameterizedTest
    @EnumSource(InteractionGeometry.Surface.class)
    void shouldShrinkBelowPreferredMinimumsOnTinyDisplays(
            InteractionGeometry.Surface surface) {
        Rectangle2D visualBounds = new Rectangle2D(-20, -15, 40, 30);

        InteractionGeometry.Layout layout = InteractionGeometry.calculate(
                visualBounds, surface, 1000.0);

        assertContained(visualBounds, layout.stageBounds());
        assertTrue(layout.stageBounds().getWidth() <= visualBounds.getWidth());
        assertTrue(layout.stageBounds().getHeight() <= visualBounds.getHeight());
    }

    @Test
    void shouldRejectInvalidInput() {
        assertThrows(NullPointerException.class, () -> InteractionGeometry.calculate(
                null, InteractionGeometry.Surface.CHOICE, 100));
        assertThrows(NullPointerException.class, () -> InteractionGeometry.calculate(
                new Rectangle2D(0, 0, 100, 100), null, 100));
        assertThrows(IllegalArgumentException.class, () -> InteractionGeometry.calculate(
                new Rectangle2D(0, 0, 0, 100), InteractionGeometry.Surface.CHOICE, 100));
        assertThrows(IllegalArgumentException.class, () -> InteractionGeometry.calculate(
                new Rectangle2D(0, 0, 100, 100), InteractionGeometry.Surface.CHOICE,
                Double.NaN));
    }

    private static void assertContained(Rectangle2D outer, Rectangle2D inner) {
        assertTrue(inner.getMinX() >= outer.getMinX());
        assertTrue(inner.getMinY() >= outer.getMinY());
        assertTrue(inner.getMaxX() <= outer.getMaxX());
        assertTrue(inner.getMaxY() <= outer.getMaxY());
    }
}

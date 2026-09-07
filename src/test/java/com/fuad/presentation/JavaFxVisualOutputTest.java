package com.fuad.presentation;

import com.fuad.audio.AssistantAudioSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import javafx.geometry.Rectangle2D;

import static org.junit.jupiter.api.Assertions.assertFalse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JavaFxVisualOutputTest {

    @Test
    void shouldExposeMutedStatus() {
        AssistantAudioSnapshot snapshot = new AssistantAudioSnapshot(40, true);

        assertEquals("\u25CF  MUTED", JavaFxVisualOutput.buildStatusText(snapshot));
    }

    @Test
    void shouldExposeLowVolumeStatus() {
        AssistantAudioSnapshot snapshot = new AssistantAudioSnapshot(10, false);

        assertEquals("\u25CF  VOL 10%", JavaFxVisualOutput.buildStatusText(snapshot));
    }

    @Test
    void shouldRejectNullStatusSnapshot() {
        assertThrows(NullPointerException.class, () -> JavaFxVisualOutput.buildStatusText(null));
    }

    @ParameterizedTest
    @CsvSource({
            "0, 6.0",
            "36, 6.0",
            "37, 7.0",
            "180, 14.0",
            "468, 30.0",
            "1000, 30.0"
    })
    void shouldCalculateBoundedDisplayDuration(int characterCount, double expectedSeconds) {
        assertEquals(expectedSeconds, JavaFxVisualOutput.calculateDisplaySeconds(characterCount));
    }

    @Test
    void shouldRejectNegativeCharacterCount() {
        assertThrows(IllegalArgumentException.class, () -> JavaFxVisualOutput.calculateDisplaySeconds(-1));
    }

    @ParameterizedTest
    @CsvSource({"80, 132", "400, 400", "2000, 864"})
    void shouldGrowUpwardAndCapAtScreenHeight(double preferred, double expected) {
        Rectangle2D bounds = JavaFxVisualOutput.calculateOverlayBounds(
                new Rectangle2D(0, 0, 1920, 1080), preferred);
        assertEquals(560, bounds.getWidth());
        assertEquals(expected, bounds.getHeight());
        assertEquals(1896, bounds.getMaxX());
        assertEquals(1056, bounds.getMaxY());
    }

    @Test
    void shouldFitSmallMonitorWithNegativeOrigin() {
        Rectangle2D bounds = JavaFxVisualOutput.calculateOverlayBounds(
                new Rectangle2D(-320, -200, 320, 200), 900);
        assertEquals(new Rectangle2D(-296, -176, 272, 152), bounds);
    }

    @Test
    void shouldShrinkAfterLongResponse() {
        Rectangle2D screen = new Rectangle2D(0, 0, 1280, 720);
        assertEquals(576, JavaFxVisualOutput.calculateOverlayBounds(screen, 2000).getHeight());
        assertEquals(132, JavaFxVisualOutput.calculateOverlayBounds(screen, 80).getHeight());
    }

    @Test
    void shouldRejectInvalidDimensions() {
        assertThrows(IllegalArgumentException.class, () -> JavaFxVisualOutput.calculateOverlayBounds(
                new Rectangle2D(0, 0, 0, 720), 100));
        assertThrows(IllegalArgumentException.class, () -> JavaFxVisualOutput.calculateOverlayBounds(
                new Rectangle2D(0, 0, 1280, 720), Double.NaN));
    }

    @Test
    void shouldPackageOverlayStylesheet() {
        var stylesheet = JavaFxVisualOutput.class.getResource("/ui/response.css");
        assertNotNull(stylesheet);
        assertFalse(stylesheet.toExternalForm().contains("test-classes"));
    }
}

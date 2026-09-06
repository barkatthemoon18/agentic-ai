package com.fuad.presentation;

import com.fuad.audio.AssistantAudioSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

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

    @Test
    void shouldCalculateClippedFrameGeometry() {
        List<Double> points = JavaFxVisualOutput.calculateFramePoints(440.0, 132.0, 14.0);

        assertEquals(List.of(
                14.0, 0.0,
                426.0, 0.0,
                440.0, 14.0,
                440.0, 118.0,
                426.0, 132.0,
                14.0, 132.0,
                0.0, 118.0,
                0.0, 14.0
        ), points);
    }

    @Test
    void shouldRejectInvalidFrameDimensions() {
        assertThrows(
                IllegalArgumentException.class,
                () -> JavaFxVisualOutput.calculateFramePoints(0.0, 132.0, 14.0));
    }

    @Test
    void shouldRejectCornerCutLargerThanFrame() {
        assertThrows(
                IllegalArgumentException.class,
                () -> JavaFxVisualOutput.calculateFramePoints(20.0, 20.0, 11.0));
    }

    @Test
    void shouldPackageOverlayStylesheet() {
        assertNotNull(JavaFxVisualOutput.class.getResource("/ui/response.css"));
    }
}

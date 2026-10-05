package com.fuad.speech.validation;

import com.fuad.speech.SpeechSegment;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BasicSpeechSegmentValidatorTest {
    private final BasicSpeechSegmentValidator validator = new BasicSpeechSegmentValidator(100, 0.1, 0.2);

    @Test
    void shouldRejectNullAndEmptySamples() {
        SpeechValidationResult nullSamples = validator.validate(new SpeechSegment(null, 16_000, 0));
        SpeechValidationResult emptySamples = validator.validate(new SpeechSegment(new float[0], 16_000, 0));

        assertFalse(nullSamples.valid());
        assertEquals("empty segment", nullSamples.reason());
        assertFalse(emptySamples.valid());
    }

    @Test
    void shouldRejectSegmentThatIsTooShortBeforeOtherThresholds() {
        SpeechValidationResult result = validator.validate(segment(50, 0.5f));

        assertFalse(result.valid());
        assertEquals("too short", result.reason());
        assertEquals(50.0, result.durationMillis());
    }

    @Test
    void shouldRejectLowRms() {
        SpeechValidationResult result = validator.validate(segment(100, 0.05f));

        assertFalse(result.valid());
        assertEquals("RMS too low", result.reason());
    }

    @Test
    void shouldRejectLowPeakEvenWhenRmsPasses() {
        float[] samples = new float[100];
        java.util.Arrays.fill(samples, 0.15f);
        SpeechValidationResult result = validator.validate(new SpeechSegment(samples, 1_000, 0));

        assertFalse(result.valid());
        assertEquals("Peak too low", result.reason());
    }

    @Test
    void shouldAcceptValuesAtThresholdAndReportMetrics() {
        SpeechValidationResult result = validator.validate(segment(100, 0.2f));

        assertTrue(result.valid());
        assertEquals("valid", result.reason());
        assertEquals(100.0, result.durationMillis());
        assertEquals(0.2, result.rms(), 1e-6);
        assertEquals(0.2, result.peak(), 1e-6);
    }

    private SpeechSegment segment(int durationMillis, float amplitude) {
        float[] samples = new float[durationMillis];
        java.util.Arrays.fill(samples, amplitude);
        return new SpeechSegment(samples, 1_000, 0);
    }
}

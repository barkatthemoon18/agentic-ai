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

        assertFalse(nullSamples.isValid());
        assertEquals("empty segment", nullSamples.getReason());
        assertFalse(emptySamples.isValid());
    }

    @Test
    void shouldRejectSegmentThatIsTooShortBeforeOtherThresholds() {
        SpeechValidationResult result = validator.validate(segment(50, 0.5f));

        assertFalse(result.isValid());
        assertEquals("too short", result.getReason());
        assertEquals(50.0, result.getDurationMillis());
    }

    @Test
    void shouldRejectLowRms() {
        SpeechValidationResult result = validator.validate(segment(100, 0.05f));

        assertFalse(result.isValid());
        assertEquals("RMS too low", result.getReason());
    }

    @Test
    void shouldRejectLowPeakEvenWhenRmsPasses() {
        float[] samples = new float[100];
        java.util.Arrays.fill(samples, 0.15f);
        SpeechValidationResult result = validator.validate(new SpeechSegment(samples, 1_000, 0));

        assertFalse(result.isValid());
        assertEquals("Peak too low", result.getReason());
    }

    @Test
    void shouldAcceptValuesAtThresholdAndReportMetrics() {
        SpeechValidationResult result = validator.validate(segment(100, 0.2f));

        assertTrue(result.isValid());
        assertEquals("valid", result.getReason());
        assertEquals(100.0, result.getDurationMillis());
        assertEquals(0.2, result.getRms(), 1e-6);
        assertEquals(0.2, result.getPeak(), 1e-6);
    }

    private SpeechSegment segment(int durationMillis, float amplitude) {
        float[] samples = new float[durationMillis];
        java.util.Arrays.fill(samples, amplitude);
        return new SpeechSegment(samples, 1_000, 0);
    }
}

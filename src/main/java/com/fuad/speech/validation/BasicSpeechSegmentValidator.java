package com.fuad.speech.validation;

import com.fuad.speech.SpeechSegment;

public class BasicSpeechSegmentValidator implements SpeechSegmentValidator{
    private final double minDurationMillis;
    private final double minRms;
    private final double minPeak;

    public  BasicSpeechSegmentValidator(double durationMillis, double rms, double peak) {
        this.minDurationMillis = durationMillis;
        this.minRms = rms;
        this.minPeak = peak;
    }

    @Override
    public SpeechValidationResult validate(SpeechSegment speechSegment) {
        float[] samples = speechSegment.getSamples();
        if (samples == null || samples.length == 0) {
            return new SpeechValidationResult(false, "empty segment", 0, 0, 0);
        }
        double durationMillis = ((double) samples.length / speechSegment.getSampleRate() * 1000.0);
        double squareSum = 0;
        double peak = 0;
        for (float sample : samples) {
            squareSum += Math.pow(sample, 2);
            double absolute = Math.abs(sample);
            if (absolute > peak) {
                peak = absolute;
            }
        }
        double rms = Math.sqrt(squareSum / samples.length);
        if (durationMillis < minDurationMillis) {
            return new SpeechValidationResult(false, "too short", durationMillis, rms, peak);
        }
        if (rms < minRms) {
            return new SpeechValidationResult(false, "RMS too low", durationMillis, rms, peak);
        }
        if (peak < minPeak) {
            return new SpeechValidationResult(false, "Peak too low", durationMillis, rms, peak);
        }
        return new SpeechValidationResult(true, "valid", durationMillis, rms, peak);
    }
}

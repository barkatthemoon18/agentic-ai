package com.fuad.pipeline;

import java.util.Objects;

public final class VoiceSignalSnapshot {
    private static final VoiceSignalSnapshot SILENCE = new VoiceSignalSnapshot(new float[0], 0.0, 0.0, 0.0);
    private final float[] samples;
    private final double rms;
    private final double peak;
    private final double vadProbability;

    public VoiceSignalSnapshot(float[] samples, double rms, double peak, double vadProbability) {
        this.samples = Objects.requireNonNull(samples).clone();
        this.rms = rms;
        this.peak = peak;
        this.vadProbability = vadProbability;
    }

    public static VoiceSignalSnapshot silence() {
        return SILENCE;
    }

    public int sampleCount() {
        return samples.length;
    }

    public float sampleAt(int index) {
        return samples[index];
    }

    public double rms() {
        return rms;
    }

    public double peak() {
        return peak;
    }

    public double vadProbability() {
        return vadProbability;
    }
}

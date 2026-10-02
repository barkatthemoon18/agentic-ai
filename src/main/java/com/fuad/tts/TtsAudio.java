package com.fuad.tts;

/** Samples are an owned, read-only-by-convention buffer; construction and access are zero-copy. */
public record TtsAudio(
        float[] samples,
        int sampleRate) {

    public int sampleCount() {
        return samples.length;
    }

    public double durationSeconds() {
        return (double) samples.length / sampleRate;
    }
}

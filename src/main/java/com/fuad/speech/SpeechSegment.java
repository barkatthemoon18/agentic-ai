package com.fuad.speech;

/** Samples are an owned, read-only-by-convention buffer; construction and access are zero-copy. */
public record SpeechSegment(
        float[] samples,
        int sampleRate,
        long startTimestampNanos) {

    public int sampleCount() {
        return samples.length;
    }

    public double durationMillis() {
        return samples.length * 1000.0 / sampleRate;
    }
}

package com.fuad.audio;

/** Samples are an owned, read-only-by-convention buffer; construction and access are zero-copy. */
public record AudioFrame(
        float[] samples,
        int sampleRate,
        long timestampNanos) {

    public int sampleCount() {
        return samples.length;
    }

    public double durationMillis() {
        return sampleCount() * 1000.0 / sampleRate;
    }
}

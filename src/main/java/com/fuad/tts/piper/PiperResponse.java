package com.fuad.tts.piper;

/** Samples are an owned, read-only-by-convention buffer; construction and access are zero-copy. */
public record PiperResponse(
        int status,
        int sampleRate,
        float[] samples,
        String message) {

    public int sampleCount() {
        return samples.length;
    }
}

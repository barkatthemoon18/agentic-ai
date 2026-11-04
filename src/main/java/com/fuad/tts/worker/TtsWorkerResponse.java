package com.fuad.tts.worker;

/** Samples are an owned, read-only-by-convention buffer; construction and access are zero-copy. */
public record TtsWorkerResponse(
        int status,
        int sampleRate,
        float[] samples,
        String message) {

    public int sampleCount() {
        return samples.length;
    }
}

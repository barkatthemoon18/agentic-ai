package com.fuad.audio;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@AllArgsConstructor
public class AudioFrame {
    float[] samples;
    int sampleRate;
    long timestampNanos;

    public int getSamplesCount() {
        return samples.length;
    }

    public double durationMillis() {
        return getSamplesCount() * 1000.0 / sampleRate;
    }
}

package com.fuad.speech;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class SpeechSegment {
    float[] samples;
    int sampleRate;
    long startTimestampNanos;

    public int getSamplesCount() {
        return samples.length;
    }

    public double durationMillis() {
        return samples.length * 1000.0 / sampleRate;
    }
}

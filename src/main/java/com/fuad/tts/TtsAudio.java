package com.fuad.tts;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class TtsAudio {
    private final float[] samples;
    private final int sampleRate;

    public int getSamplesCount() {
        return samples.length;
    }

    public double durationSeconds() {
        return (double) samples.length / sampleRate;
    }
}

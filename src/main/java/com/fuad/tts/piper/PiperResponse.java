package com.fuad.tts.piper;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PiperResponse {
    private final int status;
    private final int sampleRate;
    private final float[] samples;
    private final String message;

    public int getSampleCount() {
        return samples.length;
    }
}

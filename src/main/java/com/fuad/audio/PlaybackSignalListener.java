package com.fuad.audio;

@FunctionalInterface
public interface PlaybackSignalListener {

    void onSamples(float[] samples);

    static PlaybackSignalListener noop() {
        return samples -> { /* Empty intentionally */ };
    }
}

package com.fuad.audio;

@FunctionalInterface
public interface AudioFrameListener {
    void onFrame(AudioFrame frame);
}

package com.fuad.vad;

import com.fuad.audio.AudioFrame;

public interface VadEngine extends AutoCloseable {
    VadResult process(AudioFrame frame);
    void reset();
    @Override
    void close();
}

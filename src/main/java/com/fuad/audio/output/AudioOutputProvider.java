package com.fuad.audio.output;

public interface AudioOutputProvider extends AutoCloseable {
    AudioOutputSnapshot current();

    static AudioOutputProvider unavailable() {
        return AudioOutputSnapshot::unavailable;
    }

    @Override
    default void close() {
        /* Awaiting */
    }
}

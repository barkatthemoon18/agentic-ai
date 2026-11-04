package com.fuad.tts;

public interface TtsEngine extends AutoCloseable {
    /** Prepares the engine (e.g. spawns its worker). Engines that need no preparation keep the default no-op. */
    default void start() throws Exception {
        /* Nothing to prepare by default */
    }

    TtsAudio synthesize(String text);
    @Override
    void close() throws Exception;
}

package com.fuad.tts;

public interface TtsEngine extends AutoCloseable {
    TtsAudio synthesize(String text);
    @Override
    void close() throws Exception;
}

package com.fuad.tts.piper;

import com.fuad.tts.TtsAudio;
import com.fuad.tts.TtsEngine;

public class PiperTtsEngine implements TtsEngine {
    private final PiperClient client;

    public PiperTtsEngine(PiperClient client) {
        this.client = client;
    }

    @Override
    public TtsAudio synthesize(String text) {
        return client.synthesize(text);
    }

    @Override
    public void close() throws Exception {
        client.close();
    }
}

package com.fuad.tts.supertonic;

import com.fuad.tts.TtsAudio;
import com.fuad.tts.TtsEngine;

public class SupertonicTtsEngine implements TtsEngine {
    private final SupertonicClient client;

    public SupertonicTtsEngine(SupertonicClient client) {
        this.client = client;
    }

    @Override
    public void start() {
        client.start();
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

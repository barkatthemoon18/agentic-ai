package com.fuad.tts.supertonic;

import com.fuad.tts.TtsSettings;
import com.fuad.tts.worker.TtsWorkerClient;

import java.util.List;

public class SupertonicClient extends TtsWorkerClient {
    public SupertonicClient(TtsSettings.Supertonic settings) {
        super("Supertonic", command(settings));
    }

    static List<String> command(TtsSettings.Supertonic settings) {
        return List.of("python", "python/supertonic_worker.py",
                "--model-dir", settings.modelDir(),
                "--voice", settings.voice(),
                "--lang", settings.lang(),
                "--speed", String.valueOf(settings.speed()),
                "--steps", String.valueOf(settings.steps()),
                "--silence-duration", String.valueOf(settings.silenceDuration()),
                "--chunk-length", String.valueOf(settings.chunkLength()));
    }
}

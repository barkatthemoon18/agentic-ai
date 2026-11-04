package com.fuad.tts.piper;

import com.fuad.tts.worker.TtsWorkerClient;

import java.util.List;

public class PiperClient extends TtsWorkerClient {
    public PiperClient() {
        super("Piper", List.of("python", "python/piper_worker.py"));
    }
}

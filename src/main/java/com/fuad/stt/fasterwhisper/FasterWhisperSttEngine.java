package com.fuad.stt.fasterwhisper;

import com.fuad.speech.SpeechSegment;
import com.fuad.stt.SttEngine;
import com.fuad.stt.TranscriptionResult;

public class FasterWhisperSttEngine implements SttEngine {
    private final FasterWhisperClient client;

    public FasterWhisperSttEngine(FasterWhisperClient client) {
        this.client = client;
    }

    @Override
    public TranscriptionResult transcribe(SpeechSegment segment) {
        return client.transcribe(segment);
    }

    @Override
    public void close() {
        client.close();
    }
}

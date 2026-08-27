package com.fuad.stt;

import com.fuad.speech.SpeechSegment;

public interface SttEngine extends AutoCloseable {
    TranscriptionResult transcribe(SpeechSegment segment);
    @Override
    void close();
}

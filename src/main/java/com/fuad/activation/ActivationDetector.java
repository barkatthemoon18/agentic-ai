package com.fuad.activation;

import com.fuad.stt.TranscriptionResult;

public interface ActivationDetector {
    ActivationResult detect(TranscriptionResult transcriptionResult);
}

package com.fuad.activation.utterance;

import com.fuad.enums.UtteranceDecision;

public interface UtteranceClassifier {
    UtteranceDecision classify(UtteranceClassificationRequest request);
}

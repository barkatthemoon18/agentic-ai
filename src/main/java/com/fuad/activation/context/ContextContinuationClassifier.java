package com.fuad.activation.context;

import com.fuad.enums.ContextContinuationDecision;

public interface ContextContinuationClassifier {
    ContextContinuationDecision classify(ContextContinuationRequest continuationRequest);
}

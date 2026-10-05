package com.fuad.activation.wake;

import com.fuad.vad.WakeResolution;

public interface WakeClassifier {
    WakeResolution classify(String candidate, String remainder);
}

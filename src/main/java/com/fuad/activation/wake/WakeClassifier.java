package com.fuad.activation.wake;

import com.fuad.enums.WakeResolution;

public interface WakeClassifier {
    WakeResolution classify(String candidate, String remainder);
}

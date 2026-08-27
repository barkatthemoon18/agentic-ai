package com.fuad.speech.validation;

import com.fuad.speech.SpeechSegment;

public interface SpeechSegmentValidator {
    SpeechValidationResult validate(SpeechSegment speechSegment);
}

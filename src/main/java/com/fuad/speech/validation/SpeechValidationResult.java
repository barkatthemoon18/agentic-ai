package com.fuad.speech.validation;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class SpeechValidationResult {
    private final boolean valid;
    private final String reason;
    private final double durationMillis;
    private final double rms;
    private final double peak;
}

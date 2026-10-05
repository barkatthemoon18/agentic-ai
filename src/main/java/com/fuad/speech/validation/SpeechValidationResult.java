package com.fuad.speech.validation;

public record SpeechValidationResult(
        boolean valid,
        String reason,
        double durationMillis,
        double rms,
        double peak) {
}

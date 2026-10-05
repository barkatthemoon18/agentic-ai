package com.fuad.stt;

public record TranscriptionResult(
        String text,
        String language,
        double durationSeconds) {
}

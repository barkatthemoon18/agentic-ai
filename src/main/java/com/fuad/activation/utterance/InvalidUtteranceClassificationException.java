package com.fuad.activation.utterance;

public final class InvalidUtteranceClassificationException extends IllegalStateException {
    private final String model;
    private final int attempts;

    public InvalidUtteranceClassificationException(String message, String model, int attempts, Throwable cause) {
        super(message, cause);
        this.model = model;
        this.attempts = attempts;
    }

    public String model() {
        return model;
    }

    public int attempts() {
        return attempts;
    }
}

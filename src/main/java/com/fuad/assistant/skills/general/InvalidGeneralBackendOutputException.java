package com.fuad.assistant.skills.general;

public final class InvalidGeneralBackendOutputException extends IllegalStateException {
    private final int attempts;

    public InvalidGeneralBackendOutputException(String message, int attempts, Throwable cause) {
        super(message, cause);
        this.attempts = attempts;
    }

    public int getAttempts() {
        return attempts;
    }
}

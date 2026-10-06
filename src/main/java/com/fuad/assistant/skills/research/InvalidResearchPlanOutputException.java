package com.fuad.assistant.skills.research;

public final class InvalidResearchPlanOutputException extends IllegalStateException {
    private final int attempts;

    public InvalidResearchPlanOutputException(String message, int attempts, Throwable cause) {
        super(message, cause);
        this.attempts = attempts;
    }

    public int getAttempts() {
        return attempts;
    }
}

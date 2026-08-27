package com.fuad.assistant.session;

public class ConversationSession {
    private static final long TIMEOUT_MS = 30_000;
    private long activeUntil = 0;

    public void activate() {
        activeUntil = System.currentTimeMillis() + TIMEOUT_MS;
    }

    public void refresh() {
        activeUntil = System.currentTimeMillis() + TIMEOUT_MS;
    }

    public boolean isActive() {
        return System.currentTimeMillis() < activeUntil;
    }

    public boolean hasExpired() {
        return activeUntil != 0 && System.currentTimeMillis() >= activeUntil;
    }

    public void close() {
        activeUntil = 0;
    }
}

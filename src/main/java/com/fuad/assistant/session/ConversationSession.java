package com.fuad.assistant.session;

import java.util.Objects;
import java.util.Optional;

public class ConversationSession {
    private static final long TIMEOUT_MS = 30_000;
    private long activeUntil = 0;
    private ConversationSnapshot conversationSnapshot;

    public void openOrRefresh(ConversationSnapshot conversationSnapshot) {
        this.conversationSnapshot = Objects.requireNonNull(conversationSnapshot, "conversationSnapshot cannot be null");
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
        conversationSnapshot = null;
    }

    public Optional<ConversationSnapshot> getSnapshot() {
        if (!isActive()) {
            return Optional.empty();
        }
        return Optional.ofNullable(conversationSnapshot);
    }
}

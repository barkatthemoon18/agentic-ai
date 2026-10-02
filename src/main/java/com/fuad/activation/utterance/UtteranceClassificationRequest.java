package com.fuad.activation.utterance;

import com.fuad.assistant.session.ConversationSnapshot;

import java.util.Objects;
import java.util.Optional;

public record UtteranceClassificationRequest(
        String currentText,
        Optional<ConversationSnapshot> previousTurn) {

    public UtteranceClassificationRequest {
        String normalized = Objects.requireNonNull(currentText, "currentText cannot be null").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("currentText cannot be empty");
        }
        currentText = normalized;
        previousTurn = Objects.requireNonNull(previousTurn, "previousTurn cannot be null");
    }

    public static UtteranceClassificationRequest withoutContext(String currentText) {
        return new UtteranceClassificationRequest(currentText, Optional.empty());
    }

    public static UtteranceClassificationRequest withContext(String currentText, ConversationSnapshot conversationSnapshot) {
        return new UtteranceClassificationRequest(currentText,
                Optional.of(Objects.requireNonNull(conversationSnapshot, "conversationSnapshot cannot be null")));
    }
}

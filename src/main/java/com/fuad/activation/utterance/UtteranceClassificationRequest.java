package com.fuad.activation.utterance;

import com.fuad.assistant.session.ConversationSnapshot;
import lombok.Getter;

import java.util.Objects;
import java.util.Optional;

@Getter
public class UtteranceClassificationRequest {
    private final String currentText;
    private final Optional<ConversationSnapshot> previousTurn;

    public UtteranceClassificationRequest(String currentText, Optional<ConversationSnapshot> previousTurn) {
        String normalized = Objects.requireNonNull(currentText, "currentText cannot be null").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("currentText cannot be empty");
        }
        this.currentText = normalized;
        this.previousTurn = Objects.requireNonNull(previousTurn, "previousTurn cannot be null");
    }

    public static UtteranceClassificationRequest withoutContext(String currentText) {
        return new UtteranceClassificationRequest(currentText, Optional.empty());
    }

    public static UtteranceClassificationRequest withContext(String currentText, ConversationSnapshot conversationSnapshot) {
        return new UtteranceClassificationRequest(currentText,
                Optional.of(Objects.requireNonNull(conversationSnapshot, "conversationSnapshot cannot be null")));
    }
}

package com.fuad.evaluation.utterance;

import com.fuad.activation.utterance.UtteranceClassificationRequest;
import com.fuad.assistant.session.ConversationSnapshot;
import com.fuad.enums.Capability;
import com.fuad.enums.UtteranceDecision;

import java.util.List;
import java.util.Locale;

public record UtteranceEvaluationCase(
        String id,
        String currentText,
        Boolean contextAvailable,
        String previousUserText,
        String previousAssistantText,
        String owner,
        String expected,
        List<String> tags,
        String rationale) {

    public UtteranceDecision expectedDecision() {
        return UtteranceDecision.valueOf(expected.trim().toUpperCase(Locale.ROOT));
    }

    public UtteranceClassificationRequest toClassificationRequest() {
        if (!Boolean.TRUE.equals(contextAvailable)) {
            return UtteranceClassificationRequest.withoutContext(currentText);
        }
        ConversationSnapshot snapshot = new ConversationSnapshot(
                parseOwner(), previousUserText, previousAssistantText);
        return UtteranceClassificationRequest.withContext(currentText, snapshot);
    }

    public boolean hasTag(String tag) {
        return tags != null && tags.stream().anyMatch(value -> value.equalsIgnoreCase(tag));
    }

    private Capability parseOwner() {
        try {
            return Capability.fromValue(owner);
        } catch (IllegalArgumentException exception) {
            return Capability.valueOf(owner.trim().toUpperCase(Locale.ROOT));
        }
    }
}

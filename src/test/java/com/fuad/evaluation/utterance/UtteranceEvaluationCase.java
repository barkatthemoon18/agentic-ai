package com.fuad.evaluation.utterance;

import com.fuad.activation.utterance.UtteranceClassificationRequest;
import com.fuad.assistant.session.ConversationSnapshot;
import com.fuad.enums.Capability;
import com.fuad.enums.UtteranceDecision;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Locale;

@Getter
@Setter
@NoArgsConstructor
public class UtteranceEvaluationCase {
    private String id;
    private String currentText;
    private Boolean contextAvailable;
    private String previousUserText;
    private String previousAssistantText;
    private String owner;
    private String expected;
    private List<String> tags;
    private String rationale;

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

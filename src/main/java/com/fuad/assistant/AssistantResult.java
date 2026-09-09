package com.fuad.assistant;

import com.fuad.assistant.skills.research.ResearchConversationState;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;

@Getter
@AllArgsConstructor
public class AssistantResult {
    @NonNull
    private final String text;
    private final String continuationToken;
    private final ResearchConversationState researchConversationState;

    public AssistantResult(String text) {
        this(text, null, null);
    }

    public AssistantResult(String text, String continuationToken) {
        this(text, continuationToken, null);
    }

}

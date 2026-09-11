package com.fuad.assistant;

import com.fuad.assistant.skills.general.GeneralConversationState;
import com.fuad.assistant.skills.research.ResearchConversationState;
import com.fuad.enums.ConversationPolicy;
import lombok.Getter;
import lombok.NonNull;

import java.util.Objects;

@Getter
public class AssistantResult {
    @NonNull
    private final String text;
    private final String continuationToken;
    private final ResearchConversationState researchConversationState;
    private final GeneralConversationState generalConversationState;
    private final ConversationPolicy conversationPolicyOverride;

    public AssistantResult(String text) {
        this(text, null, null, null, null);
    }

    public AssistantResult(String text, String continuationToken) {
        this(text, continuationToken, null, null, null);
    }

    public AssistantResult(String text, String continuationToken,
                           ResearchConversationState researchConversationState) {
        this(text, continuationToken, researchConversationState, null, null);
    }

    public AssistantResult(String text, String continuationToken,
                           ResearchConversationState researchConversationState,
                           GeneralConversationState generalConversationState,
                           ConversationPolicy conversationPolicyOverride) {
        this.text = Objects.requireNonNull(text, "text cannot be null");
        this.continuationToken = continuationToken;
        this.researchConversationState = researchConversationState;
        this.generalConversationState = generalConversationState;
        this.conversationPolicyOverride = conversationPolicyOverride;
    }

    public static AssistantResult preserveConversation(String text) {
        return new AssistantResult(text, null, null, null, ConversationPolicy.PRESERVE);
    }
}

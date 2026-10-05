package com.fuad.assistant;

import com.fuad.assistant.skills.general.GeneralConversationState;
import com.fuad.assistant.skills.research.ResearchConversationState;
import com.fuad.assistant.skills.os.ApplicationCatalogPayload;
import com.fuad.assistant.skills.os.OpenApplicationsPayload;
import com.fuad.assistant.skills.os.OsConversationState;
import com.fuad.pipeline.ConversationPolicy;

import java.util.Objects;

public record AssistantResult(
        String text,
        String continuationToken,
        ResearchConversationState researchConversationState,
        GeneralConversationState generalConversationState,
        ConversationPolicy conversationPolicyOverride,
        AssistantPayload payload,
        OsConversationState osConversationState) {

    public AssistantResult {
        text = Objects.requireNonNull(text, "text cannot be null");
    }

    public AssistantResult(String text) {
        this(text, null, null, null, null, null, null);
    }

    public AssistantResult(String text, String continuationToken) {
        this(text, continuationToken, null, null, null, null, null);
    }

    public AssistantResult(String text, String continuationToken,
                           ResearchConversationState researchConversationState) {
        this(text, continuationToken, researchConversationState, null, null, null, null);
    }

    public AssistantResult(String text, String continuationToken,
                           ResearchConversationState researchConversationState,
                           GeneralConversationState generalConversationState,
                           ConversationPolicy conversationPolicyOverride) {
        this(text, continuationToken, researchConversationState, generalConversationState,
                conversationPolicyOverride, null, null);
    }

    public static AssistantResult preserveConversation(String text) {
        return new AssistantResult(text, null, null, null, ConversationPolicy.PRESERVE, null, null);
    }

    public static AssistantResult catalog(String speechText, ApplicationCatalogPayload payload) {
        return new AssistantResult(speechText, null, null, null, ConversationPolicy.KEEP_OPEN,
                payload, new OsConversationState(payload.sessionId()));
    }

    public static AssistantResult openApplications(String speechText, OpenApplicationsPayload payload) {
        return new AssistantResult(speechText, null, null, null, ConversationPolicy.PRESERVE,
                payload, null);
    }
}

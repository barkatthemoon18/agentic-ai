package com.fuad.assistant;

import com.fuad.assistant.skills.general.GeneralConversationState;
import com.fuad.assistant.skills.research.ResearchConversationState;
import com.fuad.assistant.skills.os.ApplicationCatalogPayload;
import com.fuad.assistant.skills.os.OpenApplicationsPayload;
import com.fuad.assistant.skills.os.OsConversationState;
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
    private final AssistantPayload payload;
    private final OsConversationState osConversationState;

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

    public AssistantResult(String text, String continuationToken,
                           ResearchConversationState researchConversationState,
                           GeneralConversationState generalConversationState,
                           ConversationPolicy conversationPolicyOverride,
                           AssistantPayload payload,
                           OsConversationState osConversationState) {
        this.text = Objects.requireNonNull(text, "text cannot be null");
        this.continuationToken = continuationToken;
        this.researchConversationState = researchConversationState;
        this.generalConversationState = generalConversationState;
        this.conversationPolicyOverride = conversationPolicyOverride;
        this.payload = payload;
        this.osConversationState = osConversationState;
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

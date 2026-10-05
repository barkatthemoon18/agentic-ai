package com.fuad.assistant.session;

import com.fuad.assistant.skills.general.GeneralConversationState;
import com.fuad.assistant.skills.research.ResearchConversationState;
import com.fuad.assistant.skills.os.OsConversationState;
import com.fuad.enums.Capability;

import java.util.Objects;

public record ConversationSnapshot(
        Capability owner,
        String previousUserText,
        String previousAssistantText,
        String continuationToken,
        ResearchConversationState researchConversationState,
        GeneralConversationState generalConversationState,
        OsConversationState osConversationState) {

    public ConversationSnapshot {
        owner = Objects.requireNonNull(owner, "owner cannot be null");
        previousUserText = Objects.requireNonNull(previousUserText,
                "previousUserText cannot be null");
        previousAssistantText = Objects.requireNonNull(previousAssistantText,
                "previousAssistantText cannot be null");
    }

    public ConversationSnapshot(Capability owner, String previousUserText, String previousAssistantText) {
        this(owner, previousUserText, previousAssistantText, null, null, null, null);
    }

    public ConversationSnapshot(Capability owner, String previousUserText, String previousAssistantText,
                                String continuationToken) {
        this(owner, previousUserText, previousAssistantText, continuationToken, null, null, null);
    }

    public ConversationSnapshot(Capability owner, String previousUserText, String previousAssistantText,
                                String continuationToken,
                                ResearchConversationState researchConversationState) {
        this(owner, previousUserText, previousAssistantText, continuationToken,
                researchConversationState, null, null);
    }

    public ConversationSnapshot(Capability owner, String previousUserText, String previousAssistantText,
                                String continuationToken,
                                ResearchConversationState researchConversationState,
                                GeneralConversationState generalConversationState) {
        this(owner, previousUserText, previousAssistantText, continuationToken,
                researchConversationState, generalConversationState, null);
    }

}

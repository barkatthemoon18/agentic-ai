package com.fuad.assistant.session;

import com.fuad.assistant.skills.general.GeneralConversationState;
import com.fuad.assistant.skills.research.ResearchConversationState;
import com.fuad.assistant.skills.os.OsConversationState;
import com.fuad.enums.Capability;
import lombok.Getter;
import lombok.NonNull;

import java.util.Objects;

@Getter
public class ConversationSnapshot {
    @NonNull
    private final Capability owner;
    @NonNull
    private final String previousUserText;
    @NonNull
    private final String previousAssistantText;
    private final String continuationToken;
    private final ResearchConversationState researchConversationState;
    private final GeneralConversationState generalConversationState;
    private final OsConversationState osConversationState;

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

    public ConversationSnapshot(Capability owner, String previousUserText, String previousAssistantText,
                                String continuationToken,
                                ResearchConversationState researchConversationState,
                                GeneralConversationState generalConversationState,
                                OsConversationState osConversationState) {
        this.owner = Objects.requireNonNull(owner, "owner cannot be null");
        this.previousUserText = Objects.requireNonNull(previousUserText,
                "previousUserText cannot be null");
        this.previousAssistantText = Objects.requireNonNull(previousAssistantText,
                "previousAssistantText cannot be null");
        this.continuationToken = continuationToken;
        this.researchConversationState = researchConversationState;
        this.generalConversationState = generalConversationState;
        this.osConversationState = osConversationState;
    }
}

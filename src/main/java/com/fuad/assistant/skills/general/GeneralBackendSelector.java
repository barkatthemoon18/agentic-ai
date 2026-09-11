package com.fuad.assistant.skills.general;

public interface GeneralBackendSelector {
    GeneralBackendDecision select(String command, GeneralConversationState state);
}

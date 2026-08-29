package com.fuad.assistant.skills;

import com.fuad.assistant.AssistantResult;
import com.fuad.enums.ConversationPolicy;

public interface Skill {
    AssistantResult execute(String command);
    default ConversationPolicy conversationPolicy() {
        return ConversationPolicy.PRESERVE;
    }
}

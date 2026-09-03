package com.fuad.assistant.skills;

import com.fuad.assistant.AssistantResult;
import com.fuad.enums.ConversationPolicy;

public interface Skill {
    AssistantResult execute(String command);
    default AssistantResult execute(String command, String continuationToken) {
        return execute(command);
    }
    default ConversationPolicy getConversationPolicy() {
        return ConversationPolicy.PRESERVE;
    }
}

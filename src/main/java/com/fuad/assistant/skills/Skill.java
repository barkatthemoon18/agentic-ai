package com.fuad.assistant.skills;

import com.fuad.assistant.AssistantResult;
import com.fuad.assistant.session.ConversationSnapshot;
import com.fuad.enums.ConversationPolicy;

public interface Skill {
    AssistantResult execute(String command);
    default AssistantResult execute(String command, String continuationToken) {
        return execute(command);
    }
    default AssistantResult executeFollowUp(String command, ConversationSnapshot conversationSnapshot) {
        return execute(command, conversationSnapshot == null ? null : conversationSnapshot.getContinuationToken());
    }
    default ConversationPolicy getConversationPolicy() {
        return ConversationPolicy.PRESERVE;
    }
}

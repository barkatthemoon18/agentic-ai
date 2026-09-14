package com.fuad.assistant.skills;

import com.fuad.assistant.AssistantResult;
import com.fuad.assistant.session.ConversationSnapshot;
import com.fuad.enums.ConversationPolicy;

public interface Skill {
    AssistantResult execute(String command);

    default SkillExecution executeTurn(String command) {
        return SkillExecution.completed(execute(command));
    }

    default AssistantResult execute(String command, String continuationToken) {
        return execute(command);
    }

    default SkillExecution executeTurn(String command, String continuationToken) {
        return SkillExecution.completed(execute(command, continuationToken));
    }

    default AssistantResult executeFollowUp(String command, ConversationSnapshot conversationSnapshot) {
        return execute(command, conversationSnapshot == null ? null : conversationSnapshot.getContinuationToken());
    }

    default SkillExecution executeFollowUpTurn(String command, ConversationSnapshot conversationSnapshot) {
        return SkillExecution.completed(executeFollowUp(command, conversationSnapshot));
    }

    default ConversationPolicy getConversationPolicy() {
        return ConversationPolicy.PRESERVE;
    }
}

package com.fuad.assistant;

import com.fuad.enums.Capability;
import com.fuad.enums.ConversationPolicy;

public record AssistantExecutionResult(
        AssistantResult response,
        ConversationPolicy conversationPolicy,
        Capability capability) {
}

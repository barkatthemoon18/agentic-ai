package com.fuad.assistant;

import com.fuad.enums.Capability;
import com.fuad.pipeline.ConversationPolicy;

public record AssistantExecutionResult(
        AssistantResult response,
        ConversationPolicy conversationPolicy,
        Capability capability) {
}

package com.fuad.assistant;

import com.fuad.enums.Capability;
import com.fuad.enums.ConversationPolicy;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AssistantExecutionResult {
    AssistantResult response;
    ConversationPolicy conversationPolicy;
    Capability capability;
}

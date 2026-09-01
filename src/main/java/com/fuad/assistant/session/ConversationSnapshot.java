package com.fuad.assistant.session;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ConversationSnapshot {
    String previousUserText;
    String previousAssistantText;
}

package com.fuad.assistant.session;

import com.fuad.enums.Capability;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;

@Getter
@AllArgsConstructor
public class ConversationSnapshot {
    @NonNull
    private final Capability owner;
    @NonNull
    private final String previousUserText;
    @NonNull
    private final String previousAssistantText;
}

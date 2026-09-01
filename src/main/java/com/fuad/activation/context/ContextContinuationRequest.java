package com.fuad.activation.context;

import com.fuad.assistant.session.ConversationSnapshot;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

@Getter
@AllArgsConstructor
public class ContextContinuationRequest {
    @NotNull ConversationSnapshot previousTurn;
    @NotNull String currentText;
}

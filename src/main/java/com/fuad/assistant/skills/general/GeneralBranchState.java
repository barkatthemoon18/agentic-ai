package com.fuad.assistant.skills.general;

import java.util.List;

public record GeneralBranchState(String continuationToken, List<GeneralMessage> messages) {
    public GeneralBranchState {
        messages = messages == null ? List.of() : List.copyOf(messages);
    }

    public static GeneralBranchState empty() {
        return new GeneralBranchState(null, List.of());
    }
}

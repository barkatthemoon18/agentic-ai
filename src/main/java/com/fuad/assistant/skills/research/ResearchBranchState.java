package com.fuad.assistant.skills.research;

import java.util.List;

public record ResearchBranchState(String continuationToken, List<ResearchMessage> messages) {
    public ResearchBranchState {
        messages = messages == null ? List.of() : List.copyOf(messages);
    }

    public static ResearchBranchState empty() {
        return new ResearchBranchState(null, List.of());
    }
}

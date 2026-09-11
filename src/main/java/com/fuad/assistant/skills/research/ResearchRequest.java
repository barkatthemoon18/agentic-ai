package com.fuad.assistant.skills.research;

import com.fuad.enums.ResearchDepth;

import java.util.List;
import java.util.Objects;

public record ResearchRequest(String query, String instructions, int maxOutputTokens,
                              ResearchDepth depth, ResearchBranchState continuation) {
    public ResearchRequest {
        query = Objects.requireNonNull(query, "query cannot be null").trim();
        instructions = Objects.requireNonNull(instructions, "instructions cannot be null");
        Objects.requireNonNull(depth, "depth cannot be null");
        continuation = continuation == null ? ResearchBranchState.empty() : continuation;
        if (query.isEmpty()) {
            throw new IllegalArgumentException("query cannot be empty");
        }
    }

    public List<ResearchMessage> previousMessages() {
        return continuation.messages();
    }
}

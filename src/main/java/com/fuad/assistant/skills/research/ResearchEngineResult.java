package com.fuad.assistant.skills.research;

import java.util.Objects;

public record ResearchEngineResult(String text, ResearchBranchState continuation) {
    public ResearchEngineResult {
        text = Objects.requireNonNull(text, "text cannot be null").trim();
        continuation = Objects.requireNonNull(continuation, "continuation cannot be null");
        if (text.isEmpty()) {
            throw new IllegalArgumentException("text cannot be empty");
        }
    }
}

package com.fuad.assistant.skills.general;

import java.util.Objects;

public record GeneralEngineResult(String text, GeneralBranchState continuation) {
    public GeneralEngineResult {
        text = Objects.requireNonNull(text, "text cannot be null").trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("text cannot be empty");
        }
        continuation = Objects.requireNonNull(continuation, "continuation cannot be null");
    }
}

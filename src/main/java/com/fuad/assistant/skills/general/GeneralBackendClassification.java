package com.fuad.assistant.skills.general;

import java.util.Objects;

public record GeneralBackendClassification(GeneralBackend backend, int attempts) {
    public GeneralBackendClassification {
        Objects.requireNonNull(backend, "backend cannot be null");
        if (attempts < 1 || attempts > 2) {
            throw new IllegalArgumentException("attempts must be 1 or 2");
        }
    }

    public boolean firstPassValid() {
        return attempts == 1;
    }

    public boolean retryRecovered() {
        return attempts == 2;
    }
}

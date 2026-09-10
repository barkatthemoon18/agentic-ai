package com.fuad.assistant.skills.general;

import java.util.Objects;

public record GeneralBackendDecision(GeneralBackend backend, SelectionOrigin origin) {
    public GeneralBackendDecision {
        Objects.requireNonNull(backend, "backend cannot be null");
        Objects.requireNonNull(origin, "origin cannot be null");
    }
}

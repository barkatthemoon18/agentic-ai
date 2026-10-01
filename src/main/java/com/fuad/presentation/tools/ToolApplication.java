package com.fuad.presentation.tools;

import java.util.Objects;

public record ToolApplication(
        String id,
        String displayName,
        String resolverTarget) {

    public ToolApplication {
        id = requireText(id, "id");
        displayName = requireText(displayName, "displayName");
        resolverTarget = requireText(resolverTarget, "resolveTarget");
    }

    private static String requireText(String value, String field) {
        String normalized;

        Objects.requireNonNull(value, field + " must not be null");
        normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + "must not be blank");
        }
        return normalized;
    }
}

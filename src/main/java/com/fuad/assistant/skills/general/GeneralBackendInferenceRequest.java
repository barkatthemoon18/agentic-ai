package com.fuad.assistant.skills.general;

import java.util.List;
import java.util.Objects;

public record GeneralBackendInferenceRequest(String instructions, String query,
                                             List<String> allowedLabels,
                                             int maxCompletionTokens,
                                             boolean retry,
                                             String previousInvalidOutput) {
    public GeneralBackendInferenceRequest {
        requireText(instructions, "instructions");
        requireText(query, "query");
        allowedLabels = List.copyOf(Objects.requireNonNull(
                allowedLabels, "allowedLabels cannot be null"));
        if (allowedLabels.isEmpty() || allowedLabels.stream().anyMatch(
                label -> label == null || label.isBlank())) {
            throw new IllegalArgumentException("allowedLabels cannot be empty or contain blanks");
        }
        if (maxCompletionTokens <= 0) {
            throw new IllegalArgumentException("maxCompletionTokens must be positive");
        }
        if (!retry && previousInvalidOutput != null) {
            throw new IllegalArgumentException("previousInvalidOutput requires retry=true");
        }
    }

    private static void requireText(String value, String field) {
        if (Objects.requireNonNull(value, field + " cannot be null").isBlank()) {
            throw new IllegalArgumentException(field + " cannot be empty");
        }
    }
}

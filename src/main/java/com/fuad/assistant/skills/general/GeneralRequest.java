package com.fuad.assistant.skills.general;

import java.util.Objects;

public record GeneralRequest(String command, String instructions, int maxOutputTokens,
                             GeneralBranchState continuation) {
    public GeneralRequest {
        command = requireText(command, "command");
        instructions = requireText(instructions, "instructions");
        if (maxOutputTokens <= 0) {
            throw new IllegalArgumentException("maxOutputTokens must be positive");
        }
        continuation = continuation == null ? GeneralBranchState.empty() : continuation;
    }

    private static String requireText(String value, String name) {
        String normalized = Objects.requireNonNull(value, name + " cannot be null").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " cannot be empty");
        }
        return normalized;
    }
}

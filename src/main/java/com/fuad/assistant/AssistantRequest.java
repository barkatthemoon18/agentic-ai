package com.fuad.assistant;

import com.fuad.enums.ResearchDepth;

import java.util.Objects;

public record AssistantRequest(
        String command,
        String instructions,
        int maxOutputTokens,
        String continuationToken,
        ResearchDepth researchDepth) {

    public AssistantRequest {
        Objects.requireNonNull(command, "command cannot be null");
        Objects.requireNonNull(instructions, "instructions cannot be null");
        Objects.requireNonNull(researchDepth, "researchDepth cannot be null");
    }

    public AssistantRequest(String command, String instructions, int maxOutputTokens, String continuationToken) {
        this(command, instructions, maxOutputTokens, continuationToken, ResearchDepth.NONE);
    }

}

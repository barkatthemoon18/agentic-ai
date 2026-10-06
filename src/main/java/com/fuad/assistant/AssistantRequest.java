package com.fuad.assistant;

import com.fuad.assistant.skills.research.ResearchAccess;
import com.fuad.assistant.skills.research.ResearchDepth;

import java.util.Objects;

public record AssistantRequest(
        String command,
        String instructions,
        int maxOutputTokens,
        String continuationToken,
        ResearchDepth researchDepth,
        ResearchAccess researchAccess) {

    public AssistantRequest {
        Objects.requireNonNull(command, "command cannot be null");
        Objects.requireNonNull(instructions, "instructions cannot be null");
        Objects.requireNonNull(researchDepth, "researchDepth cannot be null");
        Objects.requireNonNull(researchAccess, "access cannot be null");
    }

    public AssistantRequest(String command, String instructions, int maxOutputTokens, String continuationToken) {
        this(command, instructions, maxOutputTokens, continuationToken, ResearchDepth.NONE, ResearchAccess.MODEL_KNOWLEDGE);
    }

    public AssistantRequest(String command, String instructions, int maxOutputTokens, String continuationToken,
                            ResearchDepth depth) {
        this(command, instructions, maxOutputTokens, continuationToken, depth, ResearchAccess.MODEL_KNOWLEDGE);
    }
}

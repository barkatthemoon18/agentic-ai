package com.fuad.assistant;

import com.fuad.enums.ResearchDepth;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;

@Getter
@AllArgsConstructor
public class AssistantRequest {
    @NonNull
    private final String command;
    @NonNull
    private final String instructions;
    private final int maxOutputTokens;
    private final String continuationToken;
    @NonNull
    private final ResearchDepth researchDepth;

    public AssistantRequest(String command, String instructions, int maxOutputTokens, String continuationToken) {
        this(command, instructions, maxOutputTokens, continuationToken, ResearchDepth.NONE);
    }
}

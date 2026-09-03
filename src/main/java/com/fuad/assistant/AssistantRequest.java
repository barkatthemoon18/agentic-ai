package com.fuad.assistant;

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
}

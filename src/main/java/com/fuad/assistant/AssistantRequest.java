package com.fuad.assistant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class AssistantRequest {
    private final String command;
    private final String instructions;
    private final int maxOutputTokens;
}

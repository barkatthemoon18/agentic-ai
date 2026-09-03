package com.fuad.assistant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;

@Getter
@AllArgsConstructor
public class AssistantResult {
    @NonNull
    private final String text;
    private final String continuationToken;

    public AssistantResult(String text) {
        this(text, null);
    }
}

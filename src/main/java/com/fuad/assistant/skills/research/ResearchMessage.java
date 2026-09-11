package com.fuad.assistant.skills.research;

import java.util.Objects;

public record ResearchMessage(Role role, String content) {
    public ResearchMessage {
        Objects.requireNonNull(role, "role cannot be null");
        content = Objects.requireNonNull(content, "content cannot be null").trim();
        if (content.isEmpty()) {
            throw new IllegalArgumentException("content cannot be empty");
        }
    }

    public enum Role {
        USER,
        ASSISTANT
    }
}

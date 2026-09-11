package com.fuad.assistant.skills.general;

import java.util.Objects;

public record GeneralMessage(Role role, String content) {
    public GeneralMessage {
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

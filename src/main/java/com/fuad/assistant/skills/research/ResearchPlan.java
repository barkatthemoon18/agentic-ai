package com.fuad.assistant.skills.research;

import java.util.Objects;

public record ResearchPlan(
        ResearchAccess access,
        ResearchDepth depth) {

    public ResearchPlan {
        Objects.requireNonNull(access, "access cannot be null");
        Objects.requireNonNull(depth, "depth cannot be null");

        if (depth == ResearchDepth.NONE) {
            throw new IllegalArgumentException("Research plan cannot use NONE depth");
        }
    }
}

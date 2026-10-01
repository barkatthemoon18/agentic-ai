package com.fuad.interaction;

import java.util.Objects;
import java.util.Optional;

public record ChoiceVoiceResolution(Status status, Optional<String> optionId) {
    public enum Status { RESOLVED, AMBIGUOUS, UNKNOWN }

    public ChoiceVoiceResolution {
        status = Objects.requireNonNull(status, "status must not be null");
        optionId = optionId == null ? Optional.empty()
                : optionId.map(String::trim).filter(value -> !value.isEmpty());
        if ((status == Status.RESOLVED) != optionId.isPresent()) {
            throw new IllegalArgumentException("only RESOLVED may carry an option id");
        }
    }

    public static ChoiceVoiceResolution resolved(String optionId) {
        return new ChoiceVoiceResolution(Status.RESOLVED,
                Optional.of(Objects.requireNonNull(optionId, "optionId must not be null")));
    }

    public static ChoiceVoiceResolution ambiguous() {
        return new ChoiceVoiceResolution(Status.AMBIGUOUS, Optional.empty());
    }

    public static ChoiceVoiceResolution unknown() {
        return new ChoiceVoiceResolution(Status.UNKNOWN, Optional.empty());
    }
}

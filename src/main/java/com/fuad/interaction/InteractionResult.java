package com.fuad.interaction;

import java.util.Objects;
import java.util.Optional;

public record InteractionResult<T>(Optional<String> requestId,
                                   InteractionOutcome outcome,
                                   Optional<T> value,
                                   Optional<InputModality> modality) {
    public InteractionResult {
        requestId = requestId == null ? Optional.empty() : requestId;
        outcome = Objects.requireNonNull(outcome, "outcome must not be null");
        value = value == null ? Optional.empty() : value;
        modality = modality == null ? Optional.empty() : modality;
        if (outcome == InteractionOutcome.SUBMITTED && value.isEmpty()) {
            throw new IllegalArgumentException("submitted result requires a value");
        }
        if (outcome != InteractionOutcome.SUBMITTED && value.isPresent()) {
            throw new IllegalArgumentException("only submitted results may carry a value");
        }
    }

    public static <T> InteractionResult<T> submitted(InteractionRequest<T> request,
                                                      T value, InputModality modality) {
        return new InteractionResult<>(request.requestId(), InteractionOutcome.SUBMITTED,
                Optional.of(Objects.requireNonNull(value)), Optional.of(Objects.requireNonNull(modality)));
    }

    public static <T> InteractionResult<T> terminal(InteractionRequest<T> request,
                                                     InteractionOutcome outcome,
                                                     InputModality modality) {
        return new InteractionResult<>(request.requestId(), outcome, Optional.empty(),
                Optional.ofNullable(modality));
    }
}

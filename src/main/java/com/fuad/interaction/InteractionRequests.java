package com.fuad.interaction;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

final class InteractionRequests {
    private InteractionRequests() {
    }

    static Optional<String> requestId(Optional<String> requestId) {
        Objects.requireNonNull(requestId, "requestId must not be null");
        return requestId.map(String::trim).filter(value -> !value.isEmpty());
    }

    static String prompt(String prompt) {
        Objects.requireNonNull(prompt, "prompt must not be null");
        String value = prompt.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("prompt must not be blank");
        }
        return value;
    }

    static Set<InputModality> modalities(Set<InputModality> modalities) {
        Objects.requireNonNull(modalities, "modalities must not be null");
        Set<InputModality> copy = Set.copyOf(modalities);
        if (!copy.contains(InputModality.TOUCH)) {
            throw new IllegalArgumentException("TOUCH modality is required");
        }
        return copy;
    }

    static Optional<Duration> timeout(Optional<Duration> timeout) {
        Objects.requireNonNull(timeout, "timeoutOverride must not be null");
        timeout.ifPresent(value -> {
            if (value.isZero() || value.isNegative()) {
                throw new IllegalArgumentException("timeoutOverride must be positive");
            }
        });
        return timeout;
    }
}

package com.fuad.interaction;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record TextInputRequest(Optional<String> requestId, String prompt,
                               Set<InputModality> modalities,
                               Optional<Duration> timeoutOverride,
                               FocusRequirement focusRequirement,
                               String initialValue, String placeholder,
                               boolean allowBlank, int maxLength)
        implements InteractionRequest<String> {
    public TextInputRequest {
        requestId = InteractionRequests.requestId(requestId);
        prompt = InteractionRequests.prompt(prompt);
        modalities = InteractionRequests.modalities(modalities);
        timeoutOverride = InteractionRequests.timeout(timeoutOverride);
        focusRequirement = Objects.requireNonNull(focusRequirement,
                "focusRequirement must not be null");
        initialValue = initialValue == null ? "" : initialValue;
        placeholder = placeholder == null ? "" : placeholder;
        if (maxLength < 1) {
            throw new IllegalArgumentException("maxLength must be positive");
        }
        if (initialValue.length() > maxLength) {
            throw new IllegalArgumentException("initialValue exceeds maxLength");
        }
    }
}

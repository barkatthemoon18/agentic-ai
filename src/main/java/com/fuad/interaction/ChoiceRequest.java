package com.fuad.interaction;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record ChoiceRequest(Optional<String> requestId, String prompt,
                            Set<InputModality> modalities,
                            Optional<Duration> timeoutOverride,
                            FocusRequirement focusRequirement,
                            List<ChoiceOption> options)
        implements InteractionRequest<String> {
    public ChoiceRequest {
        requestId = InteractionRequests.requestId(requestId);
        prompt = InteractionRequests.prompt(prompt);
        modalities = InteractionRequests.modalities(modalities);
        timeoutOverride = InteractionRequests.timeout(timeoutOverride);
        focusRequirement = Objects.requireNonNull(focusRequirement,
                "focusRequirement must not be null");
        options = List.copyOf(Objects.requireNonNull(options, "options must not be null"));
        if (options.size() < 2) {
            throw new IllegalArgumentException("at least two choices are required");
        }
        Set<String> ids = new HashSet<>();
        if (options.stream().anyMatch(option -> !ids.add(option.id()))) {
            throw new IllegalArgumentException("choice ids must be unique");
        }
    }
}

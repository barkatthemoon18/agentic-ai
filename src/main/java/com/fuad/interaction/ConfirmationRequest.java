package com.fuad.interaction;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record ConfirmationRequest(Optional<String> requestId, String prompt,
                                  Set<InputModality> modalities,
                                  Optional<Duration> timeoutOverride,
                                  FocusRequirement focusRequirement,
                                  String confirmLabel, String rejectLabel)
        implements InteractionRequest<Boolean> {
    public ConfirmationRequest {
        requestId = InteractionRequests.requestId(requestId);
        prompt = InteractionRequests.prompt(prompt);
        modalities = InteractionRequests.modalities(modalities);
        timeoutOverride = InteractionRequests.timeout(timeoutOverride);
        focusRequirement = Objects.requireNonNull(focusRequirement,
                "focusRequirement must not be null");
        confirmLabel = InteractionRequests.prompt(confirmLabel);
        rejectLabel = InteractionRequests.prompt(rejectLabel);
    }
}

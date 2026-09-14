package com.fuad.interaction;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

public sealed interface InteractionRequest<T>
        permits ChoiceRequest, ConfirmationRequest, TextInputRequest {
    Optional<String> requestId();

    String prompt();

    Set<InputModality> modalities();

    Optional<Duration> timeoutOverride();

    FocusRequirement focusRequirement();
}

package com.fuad.interaction;

import java.util.Objects;
import java.util.UUID;

record ActiveInteractionSnapshot(UUID sessionId, InteractionRequest<?> request,
                                 InteractionPhase phase) {
    ActiveInteractionSnapshot {
        Objects.requireNonNull(sessionId);
        Objects.requireNonNull(request);
        Objects.requireNonNull(phase);
    }
}

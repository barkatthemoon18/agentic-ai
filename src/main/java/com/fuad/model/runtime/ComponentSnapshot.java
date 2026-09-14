package com.fuad.model.runtime;

import java.time.Instant;

public record ComponentSnapshot(
        RuntimeComponent component,
        ComponentState state,
        String detail,
        RuntimeComponent blockedBy,
        int retriesUsed,
        Instant nextRetryAt,
        long generation) {
}

package com.fuad.interaction;

import java.util.UUID;

public interface InteractionLifecycleListener {

    void onVisible(UUID sessionId);
    void onCompleted(UUID sessionId, InteractionOutcome outcome);

    static InteractionLifecycleListener noop() {
        return new InteractionLifecycleListener() {
            @Override
            public void onVisible(UUID sessionId) {
                /* Empty intentionally */
            }

            @Override
            public void onCompleted(UUID sessionId, InteractionOutcome outcome) {
                /* Empty intentionally */
            }
        };
    }
}

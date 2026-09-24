package com.fuad.pipeline;

import com.fuad.enums.Capability;

import java.util.UUID;

public interface AssistantExecutionLifecycleListener {

    void onExecutionStarted(UUID executionId, Capability capability);
    void onExecutionCompleted(UUID executionId, Capability capability);

    static AssistantExecutionLifecycleListener noop() {
        return new AssistantExecutionLifecycleListener() {
            @Override
            public void onExecutionStarted(UUID executionId, Capability capability) {
                /* Empty intentionally */
            }

            @Override
            public void onExecutionCompleted(UUID executionId, Capability capability) {
                /* Empty intentionally */
            }
        };
    }
}

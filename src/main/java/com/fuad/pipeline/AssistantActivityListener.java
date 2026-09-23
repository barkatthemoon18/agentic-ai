package com.fuad.pipeline;

@FunctionalInterface
public interface AssistantActivityListener {

    void onStateChanged(AssistantActivityState state);

    static AssistantActivityListener noop() {
        return state -> { /* Intentionally empty */ };
    }
}

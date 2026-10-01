package com.fuad.presentation.core;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public final class AssistantVisualStateCoordinator {
    public enum Source {
        RESEARCH,
        VOICE,
        EXECUTION,
        INTERACTION,
        TTS
    }

    private final Map<Source, AssistantVisualState> overrides = new EnumMap<>(Source.class);
    private final Consumer<AssistantVisualState> listener;
    private AssistantVisualState baseState = AssistantVisualState.IDLE;
    private AssistantVisualState effectiveState = AssistantVisualState.IDLE;

    public AssistantVisualStateCoordinator(Consumer<AssistantVisualState> listener) {
        this.listener = Objects.requireNonNull(listener);
    }

    public void updateBaseState(AssistantVisualState state) {
        baseState = Objects.requireNonNull(state);
        recompute();
    }

    public void setOverride(Source source, AssistantVisualState state) {
        overrides.put(Objects.requireNonNull(source), Objects.requireNonNull(state));
        recompute();
    }

    public void clearOverride(Source source) {
        overrides.remove(source);
        recompute();
    }

    public AssistantVisualState getEffectiveState() {
        return effectiveState;
    }

    private void recompute() {
        AssistantVisualState next = resolveEffectiveState();

        if (next == effectiveState) {
            return;
        }
        effectiveState = next;
        listener.accept(next);
    }

    private AssistantVisualState resolveEffectiveState() {
        if (overrides.containsKey(Source.INTERACTION)) {
            return overrides.get(Source.INTERACTION);
        }
        if (overrides.containsKey(Source.TTS)) {
            return overrides.get(Source.TTS);
        }
        if (overrides.containsKey(Source.EXECUTION)) {
            return overrides.get(Source.EXECUTION);
        }
        if (overrides.containsKey(Source.RESEARCH)) {
            return overrides.get(Source.RESEARCH);
        }
        if (overrides.containsKey(Source.VOICE)) {
            return overrides.get(Source.VOICE);
        }
        return baseState;
    }
}

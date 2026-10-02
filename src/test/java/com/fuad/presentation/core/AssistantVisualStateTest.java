package com.fuad.presentation.core;

import com.fuad.pipeline.AssistantActivityState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;

import static com.fuad.presentation.core.AssistantVisualState.*;
import static com.fuad.presentation.core.AssistantVisualStateCoordinator.Source.*;
import static org.junit.jupiter.api.Assertions.*;

class AssistantVisualStateTest {
    @Test
    void overridesShouldRespectPriorityAndRestoreLatestBaseWhenCleared() {
        List<AssistantVisualState> events = new ArrayList<>();
        AssistantVisualStateCoordinator coordinator = new AssistantVisualStateCoordinator(events::add);
        coordinator.updateBaseState(DEGRADED);
        coordinator.setOverride(VOICE, LISTENING);
        coordinator.setOverride(RESEARCH, PROCESSING);
        coordinator.setOverride(EXECUTION, EXECUTING);
        coordinator.setOverride(TTS, SPEAKING);
        coordinator.setOverride(INTERACTION, INTERACTING);
        coordinator.updateBaseState(IDLE);
        assertEquals(INTERACTING, coordinator.getEffectiveState());
        coordinator.setOverride(VOICE, IDLE);
        coordinator.clearOverride(INTERACTION);
        coordinator.clearOverride(TTS);
        coordinator.clearOverride(EXECUTION);
        coordinator.clearOverride(RESEARCH);
        coordinator.clearOverride(VOICE);
        assertEquals(IDLE, coordinator.getEffectiveState());
        assertEquals(List.of(DEGRADED, LISTENING, PROCESSING, EXECUTING, SPEAKING, INTERACTING,
                SPEAKING, EXECUTING, PROCESSING, IDLE), events);
    }

    @Test
    void repeatedOrMaskedChangesShouldNotPublishDuplicateEffectiveState() {
        List<AssistantVisualState> events = new ArrayList<>();
        AssistantVisualStateCoordinator coordinator = new AssistantVisualStateCoordinator(events::add);
        coordinator.updateBaseState(IDLE);
        coordinator.setOverride(TTS, SPEAKING);
        coordinator.setOverride(TTS, SPEAKING);
        coordinator.setOverride(VOICE, LISTENING);
        coordinator.updateBaseState(DEGRADED);
        coordinator.clearOverride(RESEARCH);
        assertEquals(List.of(SPEAKING), events);
        coordinator.clearOverride(TTS);
        assertEquals(LISTENING, coordinator.getEffectiveState());
        coordinator.clearOverride(VOICE);
        assertEquals(DEGRADED, coordinator.getEffectiveState());
    }

    @ParameterizedTest
    @EnumSource(value = AssistantActivityState.class, names = {"LISTENING", "PROCESSING", "SPEAKING"})
    void degradationShouldOnlyReplaceIdleAndPreserveActiveWork(AssistantActivityState activity) throws Exception {
        AssistantVisualStateStore store = new AssistantVisualStateStore();
        List<AssistantVisualState> events = new ArrayList<>();
        try (var subscription = store.subscribe(events::add)) {
            store.setDegraded(true);
            store.setDegraded(true);
            store.onStateChanged(activity);
            store.onStateChanged(activity);
            assertEquals(AssistantVisualState.valueOf(activity.name()), store.current());
            store.onStateChanged(AssistantActivityState.IDLE);
            assertEquals(DEGRADED, store.current());
            store.setDegraded(false);
            assertEquals(IDLE, store.current());
            assertEquals(List.of(DEGRADED, AssistantVisualState.valueOf(activity.name()), DEGRADED, IDLE), events);
        }
        store.onStateChanged(AssistantActivityState.SPEAKING);
        assertEquals(4, events.size(), "Closed subscriptions must stop receiving changes");
    }

    @Test
    void subscriptionShouldAllowReentrantStateReadsAndRejectNullState() throws Exception {
        AssistantVisualStateStore store = new AssistantVisualStateStore();
        try (var subscription = store.subscribe(state -> assertEquals(state, store.current()))) {
            store.onStateChanged(AssistantActivityState.PROCESSING);
        }
        assertThrows(NullPointerException.class, () -> store.onStateChanged(null));
        assertThrows(NullPointerException.class, () -> store.subscribe(null));
    }
}

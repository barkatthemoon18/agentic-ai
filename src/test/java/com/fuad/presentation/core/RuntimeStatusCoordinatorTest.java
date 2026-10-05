package com.fuad.presentation.core;

import com.fuad.model.runtime.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RuntimeStatusCoordinatorTest {
    @ParameterizedTest
    @EnumSource(ComponentState.class)
    void modelStatesShouldMapToRuntimePanelStates(ComponentState state) {
        RuntimeStatusCoordinator coordinator = new RuntimeStatusCoordinator();
        coordinator.updateModels(models(RuntimeState.STARTING, state));
        assertEquals(RuntimeVisualState.valueOf(state.name()), coordinator.snapshot().phiState());
        assertEquals(RuntimeVisualState.valueOf(state.name()), coordinator.snapshot().qwenState());
        assertEquals(RuntimeVisualState.CHECKING, coordinator.snapshot().sttState());
        assertEquals(RuntimeVisualState.CHECKING, coordinator.snapshot().ttsState());
    }

    @Test
    void missingComponentsShouldDisplayOffline() {
        RuntimeStatusCoordinator coordinator = new RuntimeStatusCoordinator();
        coordinator.updateModels(new ModelRuntimeSnapshot(RuntimeState.STARTING, Map.of()));
        assertEquals(RuntimeVisualState.OFFLINE, coordinator.snapshot().phiState());
        assertEquals(RuntimeVisualState.OFFLINE, coordinator.snapshot().qwenState());
    }

    @Test
    void degradationShouldStayActiveUntilAllFailedDependenciesRecover() {
        List<Boolean> events = new ArrayList<>();
        RuntimeStatusCoordinator coordinator = new RuntimeStatusCoordinator(events::add);
        coordinator.setStt(RuntimeVisualState.READY);
        coordinator.setTts(RuntimeVisualState.LOADING);
        assertTrue(events.isEmpty());
        coordinator.updateModels(models(RuntimeState.DEGRADED, ComponentState.FAILED));
        coordinator.setStt(RuntimeVisualState.OFFLINE);
        coordinator.setTts(RuntimeVisualState.FAILED);
        coordinator.updateModels(models(RuntimeState.READY, ComponentState.READY));
        coordinator.setStt(RuntimeVisualState.READY);
        assertEquals(List.of(true), events);
        coordinator.setTts(RuntimeVisualState.READY);
        coordinator.setTts(RuntimeVisualState.READY);
        assertEquals(List.of(true, false), events);
        assertEquals(RuntimeVisualState.READY, coordinator.snapshot().sttState());
        assertEquals(RuntimeVisualState.READY, coordinator.snapshot().ttsState());
    }

    @ParameterizedTest
    @EnumSource(RuntimeVisualState.class)
    void onlyFailedOrOfflineSpeechComponentsShouldDegradeRuntime(RuntimeVisualState state) {
        List<Boolean> events = new ArrayList<>();
        RuntimeStatusCoordinator coordinator = new RuntimeStatusCoordinator(events::add);
        coordinator.setStt(state);
        coordinator.setTts(state);
        assertEquals(state == RuntimeVisualState.FAILED || state == RuntimeVisualState.OFFLINE
                ? List.of(true) : List.of(), events);
    }

    private static ModelRuntimeSnapshot models(RuntimeState runtime, ComponentState state) {
        return new ModelRuntimeSnapshot(runtime, Map.of(
                RuntimeComponent.PHI_ROUTER, new ComponentSnapshot(RuntimeComponent.PHI_ROUTER, state, "", null, 0, null, 1),
                RuntimeComponent.QWEN_MAIN, new ComponentSnapshot(RuntimeComponent.QWEN_MAIN, state, "", null, 0, null, 1)));
    }
}

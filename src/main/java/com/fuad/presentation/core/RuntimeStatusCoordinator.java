package com.fuad.presentation.core;

import com.fuad.model.runtime.ComponentSnapshot;
import com.fuad.model.runtime.ModelRuntimeSnapshot;
import com.fuad.model.runtime.RuntimeComponent;
import com.fuad.model.runtime.RuntimeState;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public final class RuntimeStatusCoordinator {
    private final AtomicBoolean modelRuntimeDegraded = new AtomicBoolean(false);
    private final AtomicBoolean lastPublishedDegraded = new AtomicBoolean(false);
    private final AtomicReference<RuntimeVisualState> phi = new AtomicReference<>(RuntimeVisualState.CHECKING);
    private final AtomicReference<RuntimeVisualState> qwen = new AtomicReference<>(RuntimeVisualState.CHECKING);
    private final AtomicReference<RuntimeVisualState> stt = new AtomicReference<>(RuntimeVisualState.CHECKING);
    private final AtomicReference<RuntimeVisualState> tts = new AtomicReference<>(RuntimeVisualState.CHECKING);
    private final Consumer<Boolean> degradationListener;

    public RuntimeStatusCoordinator() {
        this(degraded -> { /* Empty intentionally */ });
    }

    public RuntimeStatusCoordinator(Consumer<Boolean> degradationListener) {
        this.degradationListener = Objects.requireNonNull(degradationListener);
    }

    public void updateModels(ModelRuntimeSnapshot modelRuntimeSnapshot) {
        phi.set(mapModelState(modelRuntimeSnapshot.component(RuntimeComponent.PHI_ROUTER)));
        qwen.set(mapModelState(modelRuntimeSnapshot.component(RuntimeComponent.QWEN_MAIN)));
        modelRuntimeDegraded.set(modelRuntimeSnapshot.state() == RuntimeState.DEGRADED);
        publishDegradationIfChanged();
    }

    public void setStt(RuntimeVisualState state) {
        stt.set(Objects.requireNonNull(state));
        publishDegradationIfChanged();
    }

    public void setTts(RuntimeVisualState state) {
        tts.set(Objects.requireNonNull(state));
        publishDegradationIfChanged();
    }

    public CoreVisualSnapshot.RuntimeSnapshot snapshot() {
        return new CoreVisualSnapshot.RuntimeSnapshot(phi.get(), qwen.get(), stt.get(), tts.get());
    }

    private void publishDegradationIfChanged() {
        boolean degraded = modelRuntimeDegraded.get() || isUnavailable(stt.get()) || isUnavailable(tts.get());
        boolean previous = lastPublishedDegraded.getAndSet(degraded);

        if (previous == degraded) {
            return;
        }
        degradationListener.accept(degraded);
    }

    private static boolean isUnavailable(RuntimeVisualState state) {
        return state == RuntimeVisualState.FAILED || state == RuntimeVisualState.OFFLINE;
    }

    private static RuntimeVisualState mapModelState(ComponentSnapshot snapshot) {
        if (snapshot == null) {
            return RuntimeVisualState.OFFLINE;
        }
        return switch (snapshot.state()) {
            case CHECKING -> RuntimeVisualState.CHECKING;
            case LOADING -> RuntimeVisualState.LOADING;
            case READY -> RuntimeVisualState.READY;
            case RETRY_WAIT -> RuntimeVisualState.RETRY_WAIT;
            case FAILED -> RuntimeVisualState.FAILED;
        };
    }
}

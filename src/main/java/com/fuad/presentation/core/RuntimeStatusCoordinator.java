package com.fuad.presentation.core;

import com.fuad.model.runtime.ComponentSnapshot;
import com.fuad.model.runtime.ModelRuntimeSnapshot;
import com.fuad.model.runtime.RuntimeComponent;

import java.util.concurrent.atomic.AtomicReference;

public final class RuntimeStatusCoordinator {
    private final AtomicReference<RuntimeVisualState> phi = new AtomicReference<>(RuntimeVisualState.CHECKING);
    private final AtomicReference<RuntimeVisualState> qwen = new AtomicReference<>(RuntimeVisualState.CHECKING);
    private final AtomicReference<RuntimeVisualState> stt = new AtomicReference<>(RuntimeVisualState.CHECKING);
    private final AtomicReference<RuntimeVisualState> tts = new AtomicReference<>(RuntimeVisualState.CHECKING);

    public void updateModels(ModelRuntimeSnapshot modelRuntimeSnapshot) {
        phi.set(mapModelState(modelRuntimeSnapshot.component(RuntimeComponent.PHI_ROUTER)));
        qwen.set(mapModelState(modelRuntimeSnapshot.component(RuntimeComponent.QWEN_MAIN)));
    }

    public void setStt(RuntimeVisualState state) {
        stt.set(state);
    }

    public void setTts(RuntimeVisualState state) {
        tts.set(state);
    }

    public CoreVisualSnapshot.RuntimeSnapshot snapshot() {
        return new CoreVisualSnapshot.RuntimeSnapshot(phi.get(), qwen.get(), stt.get(), tts.get());
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

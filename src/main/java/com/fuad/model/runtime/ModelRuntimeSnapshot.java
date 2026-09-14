package com.fuad.model.runtime;

import java.util.Map;

public record ModelRuntimeSnapshot(RuntimeState state,
                                   Map<RuntimeComponent, ComponentSnapshot> components) {
    public ModelRuntimeSnapshot {
        components = Map.copyOf(components);
    }

    public ComponentSnapshot component(RuntimeComponent component) {
        return components.get(component);
    }

    public boolean isPhiUsable() {
        return ready(RuntimeComponent.LMS_DAEMON)
                && ready(RuntimeComponent.API_SERVER)
                && ready(RuntimeComponent.PHI_ROUTER);
    }

    public boolean isQwenUsable() {
        return ready(RuntimeComponent.LMS_DAEMON)
                && ready(RuntimeComponent.API_SERVER)
                && ready(RuntimeComponent.QWEN_MAIN);
    }

    private boolean ready(RuntimeComponent component) {
        ComponentSnapshot snapshot = components.get(component);
        return snapshot != null && snapshot.state() == ComponentState.READY;
    }
}

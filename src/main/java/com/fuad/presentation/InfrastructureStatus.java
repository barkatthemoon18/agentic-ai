package com.fuad.presentation;

import com.fuad.model.runtime.ModelRuntimeSnapshot;
import com.fuad.model.runtime.RuntimeComponent;

import java.util.Objects;
import java.util.function.Consumer;

public record InfrastructureStatus(ModelRuntimeSnapshot snapshot,
                                   Consumer<RuntimeComponent> retryAction) {
    public InfrastructureStatus {
        Objects.requireNonNull(snapshot, "snapshot cannot be null");
        Objects.requireNonNull(retryAction, "retryAction cannot be null");
    }
}

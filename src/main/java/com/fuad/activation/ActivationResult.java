package com.fuad.activation;

import com.fuad.enums.ActivationType;

public record ActivationResult(
        boolean activated,
        ActivationType type,
        String command) {

    public static ActivationResult none() {
        return new ActivationResult(false, ActivationType.NONE, "");
    }
}

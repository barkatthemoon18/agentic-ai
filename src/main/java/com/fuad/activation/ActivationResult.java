package com.fuad.activation;

public record ActivationResult(
        boolean activated,
        ActivationType type,
        String command) {

    public static ActivationResult none() {
        return new ActivationResult(false, ActivationType.NONE, "");
    }
}

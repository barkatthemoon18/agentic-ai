package com.fuad.presentation.dev;

import com.fuad.enums.OsAction;

import java.util.Objects;

public record DevActionRequest(
        OsAction action,
        String target) {

    public DevActionRequest {
        Objects.requireNonNull(action);
        Objects.requireNonNull(target);
    }
}

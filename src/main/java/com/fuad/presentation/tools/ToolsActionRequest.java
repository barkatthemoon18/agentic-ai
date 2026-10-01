package com.fuad.presentation.tools;

import com.fuad.enums.OsAction;

import java.util.Objects;

public record ToolsActionRequest(
        OsAction action,
        String target) {

    public ToolsActionRequest {
        Objects.requireNonNull(action);
        Objects.requireNonNull(target);
    }
}

package com.fuad.presentation.tools;

@FunctionalInterface
public interface ToolsActionHandler {
    boolean submit(ToolsActionRequest request);

    static ToolsActionHandler unavailable() {
        return request -> false;
    }
}

package com.fuad.presentation.tools;

@FunctionalInterface
public interface MoreToolsHandler {
    boolean open();

    static MoreToolsHandler unavailable() {
        return () -> false;
    }
}

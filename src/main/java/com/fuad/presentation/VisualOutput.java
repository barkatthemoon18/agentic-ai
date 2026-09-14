package com.fuad.presentation;

public interface VisualOutput extends AutoCloseable {
    void show(VisualMessage visualMessage);
    default void showInfrastructureStatus(InfrastructureStatus status) {
        /* Optional for non-interactive visual outputs. */
    }
    void hide();
    @Override
    default void close() {
        /* Empty for now */
    }
}

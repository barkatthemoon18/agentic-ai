package com.fuad.presentation;

public interface VisualOutput extends AutoCloseable {
    void show(VisualMessage visualMessage);
    void hide();
    @Override
    default void close() {
        /* Empty for now */
    }
}

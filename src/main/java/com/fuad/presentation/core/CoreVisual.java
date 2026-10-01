package com.fuad.presentation.core;

public interface CoreVisual extends AutoCloseable {
    void show();
    void update(CoreVisualSnapshot visualSnapshot);
    @Override
    void close();
}

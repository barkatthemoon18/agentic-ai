package com.fuad.presentation.core;

import java.util.function.Consumer;

public interface CoreVisualSource extends AutoCloseable {
    void start(Consumer<CoreVisualSnapshot> consumer);

    @Override
    void close() throws Exception;
}

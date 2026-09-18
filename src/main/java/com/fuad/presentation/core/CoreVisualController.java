package com.fuad.presentation.core;

public class CoreVisualController implements AutoCloseable {
    private final CoreVisual coreVisual;
    private final MockCoreVisualSource visualSource;

    public CoreVisualController(CoreVisual coreVisual, MockCoreVisualSource visualSource) {
        this.coreVisual = coreVisual;
        this.visualSource = visualSource;
    }

    public void start() {
        coreVisual.show();
        visualSource.start(coreVisual::update);
    }

    @Override
    public void close() throws Exception {
        visualSource.close();
        coreVisual.close();
    }
}

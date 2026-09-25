package com.fuad.presentation.dev;

@FunctionalInterface
public interface DevActionHandler {
    boolean submit(DevActionRequest request);

    static DevActionHandler unavailable() {
        return request -> false;
    }
}

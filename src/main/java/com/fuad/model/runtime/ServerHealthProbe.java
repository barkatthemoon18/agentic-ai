package com.fuad.model.runtime;

public interface ServerHealthProbe {
    Result check();

    record Result(boolean reachable, boolean healthy, String detail) {
    }
}

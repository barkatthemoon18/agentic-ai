package com.fuad.model.runtime;

public class ModelRuntimeUnavailableException extends RuntimeException {
    public ModelRuntimeUnavailableException(String message) {
        super(message);
    }

    public ModelRuntimeUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

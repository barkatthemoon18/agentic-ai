package com.fuad.assistant.routing;

public class InvalidSemanticClassificationException extends IllegalStateException {
    public InvalidSemanticClassificationException(String message) {
        super(message);
    }

    public InvalidSemanticClassificationException(String message, Throwable cause) {
        super(message, cause);
    }
}

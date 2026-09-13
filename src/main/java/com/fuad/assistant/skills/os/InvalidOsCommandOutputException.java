package com.fuad.assistant.skills.os;

final class InvalidOsCommandOutputException extends IllegalStateException {
    InvalidOsCommandOutputException(String message, Throwable cause) {
        super(message, cause);
    }
}

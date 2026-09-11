package com.fuad.assistant.local;

public class LocalQwenException extends IllegalStateException {
    private final Kind kind;

    public LocalQwenException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public LocalQwenException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }

    public boolean isUnavailable() {
        return kind == Kind.UNAVAILABLE;
    }

    public enum Kind {
        UNAVAILABLE,
        FAILURE
    }
}

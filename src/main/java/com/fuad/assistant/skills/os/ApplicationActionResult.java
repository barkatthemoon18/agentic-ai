package com.fuad.assistant.skills.os;

public record ApplicationActionResult(Status status, ApplicationRuntimeState runtimeState, String detail) {
    public enum Status {
        SUCCESS,
        NOT_RUNNING,
        NO_VISIBLE_WINDOW,
        FOCUS_REJECTED,
        PROCESS_IDENTITY_UNAVAILABLE,
        FAILED
    }

    public static ApplicationActionResult success() {
        return new ApplicationActionResult(Status.SUCCESS, null, null);
    }

    public static ApplicationActionResult status(ApplicationRuntimeState runtimeState) {
        return new ApplicationActionResult(Status.SUCCESS, runtimeState, null);
    }

    public static ApplicationActionResult of(Status status) {
        return new ApplicationActionResult(status, null, null);
    }

    public static ApplicationActionResult failed(String detail) {
        return new ApplicationActionResult(Status.FAILED, null, detail);
    }
}

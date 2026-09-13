package com.fuad.assistant.skills.os;

import java.util.List;

public record OpenApplicationsResult(Status status, List<Entry> applications,
                                     int unverifiableCount, String detail) {
    public enum Status { SUCCESS, OBSERVATION_FAILED }

    public OpenApplicationsResult {
        applications = applications == null ? List.of() : List.copyOf(applications);
        if (unverifiableCount < 0) throw new IllegalArgumentException("unverifiableCount cannot be negative");
    }

    public static OpenApplicationsResult success(List<Entry> applications, int unverifiableCount) {
        return new OpenApplicationsResult(Status.SUCCESS, applications, unverifiableCount, null);
    }

    public static OpenApplicationsResult failed(String detail) {
        return new OpenApplicationsResult(Status.OBSERVATION_FAILED, List.of(), 0, detail);
    }

    public record Entry(ApplicationDefinition application, ApplicationRuntimeState runtimeState) { }
}

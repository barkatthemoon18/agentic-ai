package com.fuad.assistant.skills.os;

import java.util.List;
import java.util.Optional;

public record ApplicationResolution(Status status, ApplicationDefinition application,
                                    List<ApplicationDefinition> candidates) {
    public enum Status { FOUND, UNKNOWN, AMBIGUOUS, CATALOG_UNAVAILABLE }

    public ApplicationResolution {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }

    public Optional<ApplicationDefinition> found() {
        return status == Status.FOUND ? Optional.of(application) : Optional.empty();
    }

    static ApplicationResolution found(ApplicationDefinition application) {
        return new ApplicationResolution(Status.FOUND, application, List.of(application));
    }

    static ApplicationResolution unknown() {
        return new ApplicationResolution(Status.UNKNOWN, null, List.of());
    }

    static ApplicationResolution unavailable() {
        return new ApplicationResolution(Status.CATALOG_UNAVAILABLE, null, List.of());
    }
}

package com.fuad.media.control;

import java.util.Objects;

public sealed interface MediaTarget permits MediaTarget.Active, MediaTarget.Application {
    static MediaTarget active() {
        return new Active();
    }

    static MediaTarget application(String applicationName) {
        return new Application(applicationName);
    }

    record Active() implements MediaTarget {
        /* Empty intentionally */
    }

    record Application(String applicationName) implements MediaTarget {
        public Application {
            applicationName = Objects.requireNonNull(applicationName).strip();
            if (applicationName.isBlank()) {
                throw new IllegalArgumentException("Application name cannot be blank");
            }
        }
    }
}

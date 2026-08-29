package com.fuad.assistant.skills.os;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class ApplicationRegistry {
    private final Map<String, ApplicationDefinition> applications;

    public ApplicationRegistry(Map<String, ApplicationDefinition> applications) {
        this.applications = Map.copyOf(applications);
    }

    public Optional<ApplicationDefinition> get(String application) {
        return application == null ? Optional.empty() : Optional.ofNullable(applications
                .get(application.toLowerCase(Locale.ROOT)));
    }
}

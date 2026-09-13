package com.fuad.assistant.skills.os;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ApplicationRegistry {
    private final ApplicationCatalog catalog;

    public ApplicationRegistry(Map<String, ApplicationDefinition> applications) {
        this(ApplicationCatalog.fixed(applications.values()));
    }

    public ApplicationRegistry(ApplicationCatalog catalog) {
        this.catalog = catalog;
    }

    public Optional<ApplicationDefinition> get(String application) {
        return resolve(application, false).found();
    }

    public ApplicationResolution resolve(String application, boolean refreshOnMiss) {
        return catalog.resolve(application, refreshOnMiss);
    }

    public List<ApplicationDefinition> search(String filter, boolean refreshIfUnavailable) {
        return catalog.search(filter, refreshIfUnavailable);
    }

    public boolean isAvailable() {
        return catalog.isAvailable();
    }

    public boolean refresh() {
        return catalog.refresh();
    }
}

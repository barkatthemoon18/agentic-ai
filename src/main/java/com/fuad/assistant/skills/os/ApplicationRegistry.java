package com.fuad.assistant.skills.os;

import java.util.List;
import java.util.Collection;
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

    public ApplicationResolution resolveAmong(String application,
                                              Collection<ApplicationDefinition> candidates) {
        return catalog.resolveAmong(application, candidates);
    }

    public String catalogKey(ApplicationDefinition application) {
        return catalog.catalogKey(application);
    }

    public String normalizeTarget(String application) {
        return catalog.normalizeTarget(application);
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

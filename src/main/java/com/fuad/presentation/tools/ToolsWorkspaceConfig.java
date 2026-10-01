package com.fuad.presentation.tools;

import java.util.*;

public record ToolsWorkspaceConfig(
        int schemaVersion,
        String workspace,
        int pageSize,
        List<String> featured,
        List<ToolApplication> applications) {
    public ToolsWorkspaceConfig {
        workspace = Objects.requireNonNull(workspace, "workspace must not be null").trim();
        featured = featured == null ? List.of() : List.copyOf(featured);
        applications = applications == null ? List.of() : List.copyOf(applications);
        if (schemaVersion != 1) {
            throw new IllegalArgumentException("Unsupported tools workspace schema: " + schemaVersion);
        }
        if (!"TOOLS".equalsIgnoreCase(workspace)) {
            throw new IllegalArgumentException("Expected workspace TOOLS but found: " + workspace);
        }
        if (pageSize < 2) {
            throw new IllegalArgumentException("Page size must be greater than two.");
        }
        Set<String> applicationsId = new HashSet<>();
        for (ToolApplication app : applications) {
            if (!applicationsId.add(app.id())) {
                throw new IllegalArgumentException("Duplicate application ID: " + app.id());
            }
        }
        Set<String> featureIds = new HashSet<>();
        for (String id : featured) {
            if (!featureIds.add(id)) {
                throw new IllegalArgumentException("Duplicate feature ID: " + id);
            }
            if (!applicationsId.contains(id)) {
                throw new IllegalArgumentException("Unknown featured application: " + id);
            }
        }
    }

    public static ToolsWorkspaceConfig empty() {
        return new ToolsWorkspaceConfig(1, "TOOLS", 6, List.of(), List.of());
    }

    public List<ToolApplication> featuredApplications() {
        Map<String, ToolApplication> byId = byId();

        return featured.stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    public List<ToolApplication> moreApplications() {
        Set<String> featuredIds = Set.copyOf(featured);

        return applications.stream().filter(application -> !featuredIds.contains(application.id())).toList();
    }

    private Map<String, ToolApplication> byId() {
        Map<String, ToolApplication> byId = new LinkedHashMap<>();

        for (ToolApplication app : applications) {
            byId.put(app.id(), app);
        }
        return byId;
    }
}

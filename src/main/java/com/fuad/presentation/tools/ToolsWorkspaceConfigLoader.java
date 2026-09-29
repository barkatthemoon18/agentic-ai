package com.fuad.presentation.tools;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class ToolsWorkspaceConfigLoader {
    private final Path path;
    private final ObjectMapper objectMapper;

    public ToolsWorkspaceConfigLoader(Path path) {
        this(path, new ObjectMapper());
    }

    private ToolsWorkspaceConfigLoader(Path path, ObjectMapper objectMapper) {
        this.path = Objects.requireNonNull(path);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    public ToolsWorkspaceConfig load() {
        if (!Files.isRegularFile(path)) {
            System.err.println("Tools workspace config not found: " + path);
            return ToolsWorkspaceConfig.empty();
        }
        try {
            return objectMapper.readValue(path.toFile(), ToolsWorkspaceConfig.class);
        }
        catch (IOException | RuntimeException e) {
            System.err.println("Unable to load tools workspace config rom: " + path + ": " + e.getMessage());
            return ToolsWorkspaceConfig.empty();
        }
    }
}

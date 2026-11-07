package com.fuad.presentation.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static com.fuad.logging.LogCategory.PRESENTATION;

public final class ToolsWorkspaceConfigLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(ToolsWorkspaceConfigLoader.class);

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
            LOGGER.warn(PRESENTATION, "Tools workspace config not found: {}", path);
            return ToolsWorkspaceConfig.empty();
        }
        try {
            ToolsWorkspaceConfig config = objectMapper.readValue(path.toFile(), ToolsWorkspaceConfig.class);
            if (config == null) {
                LOGGER.warn(PRESENTATION, "Invalid tools workspace config (JSON null): {}", path);
                return ToolsWorkspaceConfig.empty();
            }
            return config;
        }
        catch (IOException | RuntimeException e) {
            LOGGER.warn(PRESENTATION, "Unable to load tools workspace config from: {}", path, e);
            return ToolsWorkspaceConfig.empty();
        }
    }
}

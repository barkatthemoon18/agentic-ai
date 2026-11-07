package com.fuad.assistant.skills.os;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.fuad.logging.LogCategory.OS;

public class ApplicationAliasConfigLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationAliasConfigLoader.class);

    private final Path path;
    private final ObjectMapper objectMapper;

    public ApplicationAliasConfigLoader(Path path) {
        this(path, new ObjectMapper());
    }

    ApplicationAliasConfigLoader(Path path, ObjectMapper objectMapper) {
        this.path = path;
        this.objectMapper = objectMapper;
    }

    public ApplicationAliasConfig load() {
        if (path == null || !Files.isRegularFile(path)) return ApplicationAliasConfig.empty();
        try {
            return objectMapper.readValue(path.toFile(), ApplicationAliasConfig.class);
        }
        catch (IOException e) {
            LOGGER.warn(OS, "Unable to load application aliases from {}", path, e);
            return ApplicationAliasConfig.empty();
        }
    }
}

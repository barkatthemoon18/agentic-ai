package com.fuad.assistant.skills.os;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ApplicationAliasConfigLoader {
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
            System.err.println("Unable to load application aliases from " + path + ": " + e.getMessage());
            return ApplicationAliasConfig.empty();
        }
    }
}

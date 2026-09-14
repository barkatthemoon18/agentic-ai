package com.fuad.presentation.interaction;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class InteractionDisplayConfigLoader {
    private final ObjectMapper objectMapper;

    public InteractionDisplayConfigLoader() {
        this(new ObjectMapper());
    }

    InteractionDisplayConfigLoader(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    public InteractionDisplayConfig load(Path path) {
        Objects.requireNonNull(path, "path must not be null");
        if (!Files.isRegularFile(path)) {
            return InteractionDisplayConfig.defaults();
        }
        try {
            return objectMapper.readValue(path.toFile(), InteractionDisplayConfig.class);
        }
        catch (IOException e) {
            throw new IllegalStateException("Unable to read interaction display config: " + path, e);
        }
    }
}

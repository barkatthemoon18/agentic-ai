package com.fuad.assistant.skills.os;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ApplicationRegistryTest {
    @Test
    void shouldFindRegisteredApplicationIgnoringCase() {
        ApplicationDefinition spotify = spotify();
        ApplicationRegistry registry = new ApplicationRegistry(Map.of("spotify", spotify));

        assertSame(spotify, registry.get("SPOTIFY").orElseThrow());
    }

    @Test
    void shouldReturnEmptyForNullOrUnknownApplication() {
        ApplicationRegistry registry = new ApplicationRegistry(Map.of("spotify", spotify()));

        assertTrue(registry.get(null).isEmpty());
        assertTrue(registry.get("firefox").isEmpty());
    }

    @Test
    void shouldDefensivelyCopyRegistryMap() {
        Map<String, ApplicationDefinition> applications = new java.util.HashMap<>();
        applications.put("spotify", spotify());
        ApplicationRegistry registry = new ApplicationRegistry(applications);
        applications.clear();

        assertTrue(registry.get("spotify").isPresent());
    }

    private ApplicationDefinition spotify() {
        return new ApplicationDefinition("spotify", "Spotify", List.of("spotify"), "Spotify.exe");
    }
}

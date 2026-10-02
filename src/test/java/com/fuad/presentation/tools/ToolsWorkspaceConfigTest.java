package com.fuad.presentation.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ToolsWorkspaceConfigTest {
    @TempDir Path directory;
    private final ToolApplication idea = new ToolApplication("idea", "IntelliJ IDEA", "IntelliJ IDEA Ultimate");
    private final ToolApplication browser = new ToolApplication("browser", "Browser", "Firefox");
    private final ToolApplication tidal = new ToolApplication("tidal", "Music", "Tidal");

    @Test
    void featuredShouldFollowConfiguredOrderAndMoreShouldPreserveRemainingOrder() {
        List<ToolApplication> applications = new ArrayList<>(List.of(idea, browser, tidal));
        List<String> featured = new ArrayList<>(List.of("tidal", "idea"));
        ToolsWorkspaceConfig config = new ToolsWorkspaceConfig(1, " tools ", 2, featured, applications);
        featured.clear();
        applications.clear();
        assertEquals(List.of(tidal, idea), config.featuredApplications());
        assertEquals(List.of(browser), config.moreApplications());
        assertThrows(UnsupportedOperationException.class, () -> config.applications().clear());
        assertThrows(UnsupportedOperationException.class, () -> config.featured().clear());
    }

    @Test
    void invalidSchemaWorkspaceReferencesAndDuplicatesShouldBeRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ToolsWorkspaceConfig(2, "TOOLS", 6, null, null));
        assertThrows(IllegalArgumentException.class, () -> new ToolsWorkspaceConfig(1, "MEDIA", 6, null, null));
        assertThrows(IllegalArgumentException.class, () -> new ToolsWorkspaceConfig(1, "TOOLS", 1, null, null));
        assertThrows(IllegalArgumentException.class, () -> new ToolsWorkspaceConfig(1, "TOOLS", 6, List.of("unknown"), List.of(idea)));
        assertThrows(IllegalArgumentException.class, () -> new ToolsWorkspaceConfig(1, "TOOLS", 6, List.of("idea", "idea"), List.of(idea)));
        assertThrows(IllegalArgumentException.class, () -> new ToolsWorkspaceConfig(1, "TOOLS", 6, List.of(), List.of(idea, idea)));
    }

    @Test
    void applicationShouldNormalizeResolverTargetAndRejectMissingIdentity() {
        assertEquals(idea, new ToolApplication(" idea ", " IntelliJ IDEA ", " IntelliJ IDEA Ultimate "));
        assertThrows(IllegalArgumentException.class, () -> new ToolApplication(" ", "Idea", "Idea"));
        assertThrows(IllegalArgumentException.class, () -> new ToolApplication("idea", " ", "Idea"));
        assertThrows(IllegalArgumentException.class, () -> new ToolApplication("idea", "Idea", " "));
        assertThrows(NullPointerException.class, () -> new ToolApplication("idea", "Idea", null));
    }

    @Test
    void loaderShouldReadValidConfigWithDifferentDisplayAndResolverNames() throws Exception {
        Path path = directory.resolve("tools.json");
        Files.writeString(path, """
                {"schemaVersion":1,"workspace":"TOOLS","pageSize":2,"featured":["idea"],
                 "applications":[{"id":"idea","displayName":"IDE","resolverTarget":"IntelliJ IDEA Ultimate"}]}
                """);
        ToolsWorkspaceConfig config = new ToolsWorkspaceConfigLoader(path).load();
        assertEquals(2, config.pageSize());
        assertEquals("IDE", config.featuredApplications().getFirst().displayName());
        assertEquals("IntelliJ IDEA Ultimate", config.featuredApplications().getFirst().resolverTarget());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "{}", "{\"schemaVersion\":2,\"workspace\":\"TOOLS\",\"pageSize\":6}"})
    void invalidConfigShouldDegradeToAnEmptyWorkspace(String json) throws Exception {
        Path path = directory.resolve("tools.json");
        Files.writeString(path, json);
        assertEquals(ToolsWorkspaceConfig.empty(), new ToolsWorkspaceConfigLoader(path).load());
    }

    @Test
    void jsonNullShouldDegradeToAnEmptyWorkspace() throws Exception {
        Path path = directory.resolve("tools.json");
        Files.writeString(path, "null");

        assertEquals(ToolsWorkspaceConfig.empty(), new ToolsWorkspaceConfigLoader(path).load());
    }

    @Test
    void missingConfigShouldUseEmptyDefaults() {
        assertEquals(ToolsWorkspaceConfig.empty(), new ToolsWorkspaceConfigLoader(directory.resolve("missing.json")).load());
    }

    @Test
    void projectConfigurationShouldRemainLoadableAndHaveValidFeaturedTargets() throws Exception {
        // Parse directly so the loader's fallback cannot turn an invalid tracked config into a passing test.
        ToolsWorkspaceConfig config = new com.fasterxml.jackson.databind.ObjectMapper()
                .readValue(Path.of("config", "ares-tools-applications.json").toFile(), ToolsWorkspaceConfig.class);
        assertFalse(config.applications().isEmpty());
        assertFalse(config.featuredApplications().isEmpty());
        assertEquals(config.featured().size(), config.featuredApplications().size());
    }
}

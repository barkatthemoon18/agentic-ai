package com.fuad.assistant.skills.os;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ApplicationCatalogTest {
    @TempDir Path temporaryDirectory;

    @Test
    void manualAliasesShouldOverrideAutomaticAliasesAndSupportRemoval() throws Exception {
        Path config = temporaryDirectory.resolve("apps.json");
        Files.writeString(config, """
                {"aliases":{"code":"vscode-id"},"removeAliases":["Spotify"],"processNames":{}}
                """);
        ApplicationCatalog catalog = new ApplicationCatalog(
                () -> List.of(app("vscode-id", "Visual Studio Code"), app("spotify-id", "Spotify")),
                new ApplicationAliasConfigLoader(config));

        assertTrue(catalog.refresh());

        assertEquals("vscode-id", catalog.resolve("CODE", false).found().orElseThrow().getId());
        assertEquals(ApplicationResolution.Status.UNKNOWN, catalog.resolve("spotify", false).status());
        assertEquals(1, catalog.search("code", false).size());
    }

    @Test
    void unknownTargetShouldRefreshOnlyOnceBeforeReturningUnknown() {
        AtomicInteger calls = new AtomicInteger();
        ApplicationCatalog catalog = new ApplicationCatalog(() -> {
            calls.incrementAndGet();
            return List.of(app("spotify", "Spotify"));
        }, new ApplicationAliasConfigLoader(null));
        catalog.refresh();

        assertEquals(ApplicationResolution.Status.UNKNOWN, catalog.resolve("Firefox", true).status());
        assertEquals(2, calls.get());
    }

    @Test
    void initialFailureShouldLeaveCatalogUnavailableAndLaterRefreshCanRecover() {
        AtomicInteger calls = new AtomicInteger();
        ApplicationCatalog catalog = new ApplicationCatalog(() -> {
            if (calls.getAndIncrement() == 0) throw new IOException("blocked");
            return List.of(app("spotify", "Spotify"));
        }, new ApplicationAliasConfigLoader(null));

        assertFalse(catalog.refresh());
        assertFalse(catalog.isAvailable());
        assertTrue(catalog.resolve("Spotify", true).found().isPresent());
        assertTrue(catalog.isAvailable());
    }

    @Test
    void automaticAliasCollisionShouldRequireDisambiguation() {
        ApplicationCatalog catalog = new ApplicationCatalog(
                () -> List.of(app("photos-one", "Photos"), app("photos-two", "Photos")),
                new ApplicationAliasConfigLoader(null));
        catalog.refresh();

        ApplicationResolution result = catalog.resolve("photos", false);

        assertEquals(ApplicationResolution.Status.AMBIGUOUS, result.status());
        assertEquals(2, result.candidates().size());
    }

    @Test
    void exactDisplayNameShouldWinOverAnotherApplicationsExecutableAlias() {
        ApplicationDefinition firefox = new ApplicationDefinition("firefox", "Firefox", Set.of(), List.of("open"),
                new ApplicationProcessIdentity(Set.of("C:\\Mozilla\\firefox.exe"), Set.of(), Set.of()));
        ApplicationDefinition webApp = new ApplicationDefinition("prime-video", "Prime Video", Set.of(), List.of("open"),
                new ApplicationProcessIdentity(Set.of("C:\\Mozilla\\firefox.exe"), Set.of(), Set.of()));
        ApplicationCatalog catalog = new ApplicationCatalog(() -> List.of(firefox, webApp),
                new ApplicationAliasConfigLoader(null));
        catalog.refresh();

        assertEquals("firefox", catalog.resolve("Firefox", false).found().orElseThrow().getId());
    }

    private ApplicationDefinition app(String id, String name) {
        return new ApplicationDefinition(id, name, Set.of(), List.of("open", id),
                ApplicationProcessIdentity.empty());
    }
}

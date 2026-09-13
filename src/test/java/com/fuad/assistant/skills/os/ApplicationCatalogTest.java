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
    void shouldResolveNaturalNamesOnlyWhenTheResultIsUnique() {
        ApplicationCatalog catalog = new ApplicationCatalog(() -> List.of(
                app("idea", "IntelliJ IDEA 2026.2.2"),
                app("prime", "Primevideo")), new ApplicationAliasConfigLoader(null));
        catalog.refresh();

        assertEquals("idea", catalog.resolve("IntelliJ", false).found().orElseThrow().getId());
        assertEquals("prime", catalog.resolve("Prime Video", false).found().orElseThrow().getId());
        assertEquals(ApplicationResolution.Status.UNKNOWN, catalog.resolve("InteliJ", false).status());
    }

    @Test
    void naturalNameCollisionShouldReturnConcreteCandidatesWithoutRefreshing() {
        AtomicInteger calls = new AtomicInteger();
        ApplicationCatalog catalog = new ApplicationCatalog(() -> {
            calls.incrementAndGet();
            return List.of(app("ultimate", "IntelliJ IDEA Ultimate"),
                    app("community", "IntelliJ IDEA Community"));
        }, new ApplicationAliasConfigLoader(null));
        catalog.refresh();

        ApplicationResolution result = catalog.resolve("IntelliJ", true);

        assertEquals(ApplicationResolution.Status.AMBIGUOUS, result.status());
        assertEquals(List.of("IntelliJ IDEA Community", "IntelliJ IDEA Ultimate"), result.candidates().stream()
                .map(ApplicationDefinition::getDisplayName).toList());
        assertEquals(1, calls.get());
    }

    @Test
    void explicitAliasShouldOverrideNaturalResolution() throws Exception {
        Path config = temporaryDirectory.resolve("override.json");
        Files.writeString(config, """
                {"aliases":{"IntelliJ":"preferred"},"removeAliases":[],"processNames":{}}
                """);
        ApplicationCatalog catalog = new ApplicationCatalog(() -> List.of(
                app("natural", "IntelliJ IDEA"), app("preferred", "JetBrains Toolbox")),
                new ApplicationAliasConfigLoader(config));
        catalog.refresh();

        assertEquals("preferred", catalog.resolve("IntelliJ", false).found().orElseThrow().getId());
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

    @Test
    void shouldMergeRuntimeIdentityConfigurationWithoutPromotingOrdinaryProcessNames() throws Exception {
        Path config = temporaryDirectory.resolve("apps.json");
        Files.writeString(config, """
                {
                  "aliases": {},
                  "removeAliases": [],
                  "processNames": {"web-app": ["firefox.exe"]},
                  "trustedProcessNamesWhenPathUnavailable": {"web-app": ["trusted-host.exe"]},
                  "commandLineArgumentSets": {"web-app": [["--app-id=prime", "--profile=work"]]}
                }
                """);
        ApplicationCatalog catalog = new ApplicationCatalog(
                () -> List.of(app("web-app", "Prime Video")), new ApplicationAliasConfigLoader(config));

        assertTrue(catalog.refresh());
        ApplicationProcessIdentity identity = catalog.applications().getFirst().getProcessIdentity();

        assertEquals(Set.of("firefox.exe"), identity.processNames());
        assertEquals(Set.of("trusted-host.exe"), identity.trustedProcessNamesWhenPathUnavailable());
        assertEquals(List.of(Set.of("--app-id=prime", "--profile=work")),
                identity.commandLineArgumentSets());
    }

    @Test
    void shouldPreserveEmptyExactArgumentVectorAndExplicitWindowPolicy() throws Exception {
        Path config = temporaryDirectory.resolve("host.json");
        Files.writeString(config, """
                {
                  "exactCommandLineArgumentSets": {"firefox": [[], ["-os-autostart"]]},
                  "hostRelationships": {"prime": "firefox"},
                  "windowSignatures": {"firefox": [{
                    "className": "MozillaWindowClass",
                    "titlePattern": ".*Mozilla Firefox$"
                  }]},
                  "windowAssociationsEnabled": {"firefox": true}
                }
                """);
        ApplicationDefinition firefox = shared("firefox", List.of());
        ApplicationDefinition prime = shared("prime", List.of(Set.of("-taskbar-tab", "prime")));
        ApplicationCatalog catalog = new ApplicationCatalog(() -> List.of(firefox, prime),
                new ApplicationAliasConfigLoader(config));

        assertTrue(catalog.refresh());
        ApplicationProcessIdentity firefoxIdentity = catalog.applications().stream()
                .filter(app -> app.getId().equals("firefox")).findFirst().orElseThrow().getProcessIdentity();
        ApplicationProcessIdentity primeIdentity = catalog.applications().stream()
                .filter(app -> app.getId().equals("prime")).findFirst().orElseThrow().getProcessIdentity();
        assertEquals(List.of(List.of(), List.of("-os-autostart")),
                firefoxIdentity.exactCommandLineArgumentSets());
        assertTrue(firefoxIdentity.windowAssociationEnabled());
        assertEquals("firefox", primeIdentity.hostApplicationId());
    }

    @Test
    void automaticHostInferenceShouldRelateDefinitionsWithoutCreatingABaseSignature() {
        ApplicationCatalog catalog = new ApplicationCatalog(() -> List.of(
                shared("firefox", List.of()),
                shared("prime", List.of(Set.of("-taskbar-tab", "prime")))),
                new ApplicationAliasConfigLoader(null));

        assertTrue(catalog.refresh());
        ApplicationProcessIdentity firefox = catalog.applications().stream()
                .filter(app -> app.getId().equals("firefox")).findFirst().orElseThrow().getProcessIdentity();
        ApplicationProcessIdentity prime = catalog.applications().stream()
                .filter(app -> app.getId().equals("prime")).findFirst().orElseThrow().getProcessIdentity();
        assertEquals("firefox", prime.hostApplicationId());
        assertTrue(firefox.exactCommandLineArgumentSets().isEmpty());
    }

    private ApplicationDefinition app(String id, String name) {
        return new ApplicationDefinition(id, name, Set.of(), List.of("open", id),
                ApplicationProcessIdentity.empty());
    }

    private ApplicationDefinition shared(String id, List<Set<String>> argumentSets) {
        return new ApplicationDefinition(id, id, Set.of(), List.of("open"),
                new ApplicationProcessIdentity(Set.of("C:\\Mozilla\\firefox.exe"), Set.of(),
                        Set.of("firefox.exe"), Set.of(), argumentSets));
    }
}

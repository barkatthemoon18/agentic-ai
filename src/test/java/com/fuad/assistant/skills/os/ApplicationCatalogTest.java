package com.fuad.assistant.skills.os;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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

    @Test
    void transcriptionAliasesShouldResolveWholeTokenSequencesWithoutSubstringMatches() throws Exception {
        Path config = temporaryDirectory.resolve("transcription.json");
        Files.writeString(config, """
                {
                  "transcriptionAliases": {"estudio":"studio", "topas":"topaz"}
                }
                """);
        ApplicationCatalog catalog = new ApplicationCatalog(() -> List.of(
                app("android", "Android Studio"),
                app("studio-one", "Studio One 7"),
                app("visual", "Visual Studio Code"),
                app("graph", "GraphStudioNext"),
                app("photo", "Topaz Photo AI"),
                app("video", "Topaz Video AI")), new ApplicationAliasConfigLoader(config));

        assertTrue(catalog.refresh());

        assertEquals(List.of("Android Studio", "Studio One 7", "Visual Studio Code"),
                catalog.resolve("Estudio", false).candidates().stream()
                        .map(ApplicationDefinition::getDisplayName).toList());
        assertEquals("android", catalog.resolve("Android Estudio", false)
                .found().orElseThrow().getId());
        assertEquals(List.of("Topaz Photo AI", "Topaz Video AI"),
                catalog.resolve("Topás", false).candidates().stream()
                        .map(ApplicationDefinition::getDisplayName).toList());
        assertEquals("photo", catalog.resolve("Topas Photo", false)
                .found().orElseThrow().getId());
        assertEquals(ApplicationResolution.Status.UNKNOWN,
                catalog.resolve("Graph Studio", false).status());
    }

    @Test
    void normalizedTranscriptionAliasCollisionShouldRejectRefreshAndPreservePreviousState() throws Exception {
        Path config = temporaryDirectory.resolve("collision.json");
        Files.writeString(config, """
                {"transcriptionAliases":{"topas":"topaz"}}
                """);
        ApplicationCatalog catalog = new ApplicationCatalog(
                () -> List.of(app("photo", "Topaz Photo AI")),
                new ApplicationAliasConfigLoader(config));
        assertTrue(catalog.refresh());

        Files.writeString(config, """
                {"transcriptionAliases":{"Topás":"topaz","topas":"otra-cosa"}}
                """);

        assertFalse(catalog.refresh());
        assertEquals("photo", catalog.resolve("topas photo", false)
                .found().orElseThrow().getId());
    }

    @Test
    void sameAppIdShouldMergePresentationButRejectRuntimeIdentityConflicts() {
        AtomicReference<List<ApplicationDefinition>> definitions = new AtomicReference<>(List.of(
                definition("STUDIO", "Studio", Set.of("editor"), "C:\\Apps\\Studio.exe"),
                definition("studio", "Studio IDE", Set.of("ide"), "c:/apps/studio.exe")));
        ApplicationCatalog catalog = new ApplicationCatalog(definitions::get,
                new ApplicationAliasConfigLoader(null));

        assertTrue(catalog.refresh());
        assertEquals(1, catalog.applications().size());
        assertEquals("Studio", catalog.applications().getFirst().getDisplayName());
        assertTrue(catalog.applications().getFirst().getAliases().containsAll(
                Set.of("editor", "ide", "Studio IDE")));

        definitions.set(List.of(
                definition("studio", "Studio", Set.of(), "C:\\Apps\\Studio.exe"),
                definition("STUDIO", "Studio", Set.of(), "C:\\Other\\Studio.exe")));

        assertFalse(catalog.refresh());
        assertEquals("C:\\Apps\\Studio.exe", catalog.applications().getFirst()
                .getProcessIdentity().executablePaths().iterator().next());
    }

    @Test
    void missingAppIdsShouldUseFullDefinitionFingerprintForDeduplicationAndOrdering() {
        ApplicationDefinition first = definition(null, "Studio", Set.of(), "C:\\Zulu\\Studio.exe");
        ApplicationDefinition second = definition(null, "Studio", Set.of(), "C:\\Alpha\\Studio.exe");
        ApplicationCatalog forward = ApplicationCatalog.fixed(List.of(first, second, first));
        ApplicationCatalog reverse = ApplicationCatalog.fixed(List.of(second, first, first));

        assertEquals(2, forward.applications().size());
        assertEquals(forward.applications().stream().map(ApplicationCatalogIdentity::stableKey).toList(),
                reverse.applications().stream().map(ApplicationCatalogIdentity::stableKey).toList());
        assertEquals(1, ApplicationCatalog.fixed(List.of(first, first)).applications().size());
    }

    @Test
    void fingerprintsShouldEncodeCommandAndWindowStructuresWithoutDelimiterCollisions() {
        ApplicationDefinition pathAfterCommand = new ApplicationDefinition(null, "Studio", Set.of(),
                List.of("open"), new ApplicationProcessIdentity(Set.of("#"), Set.of(), Set.of()));
        ApplicationDefinition commandImpersonatingBoundary = new ApplicationDefinition(null, "Studio", Set.of(),
                List.of("open", "#", "1"), ApplicationProcessIdentity.empty());

        assertNotEquals(ApplicationCatalogIdentity.runtimeIdentityFingerprint(pathAfterCommand),
                ApplicationCatalogIdentity.runtimeIdentityFingerprint(commandImpersonatingBoundary));

        ApplicationDefinition firstWindow = definitionWithIdentity(new ApplicationProcessIdentity(
                Set.of(), Set.of(), Set.of(), Set.of(), List.of(), List.of(), "",
                List.of(new ApplicationWindowSignature("a", "b\u0000c")), true));
        ApplicationDefinition secondWindow = definitionWithIdentity(new ApplicationProcessIdentity(
                Set.of(), Set.of(), Set.of(), Set.of(), List.of(), List.of(), "",
                List.of(new ApplicationWindowSignature("a\u0000b", "c")), true));

        assertNotEquals(ApplicationCatalogIdentity.runtimeIdentityFingerprint(firstWindow),
                ApplicationCatalogIdentity.runtimeIdentityFingerprint(secondWindow));
    }

    @Test
    void fingerprintsShouldFollowRuntimeNormalizationAndPreserveExactArguments() {
        ApplicationDefinition first = new ApplicationDefinition(null, "Stúdio", Set.of("IDE", "Editor"),
                List.of("C:\\Tools\\.\\Launcher.exe", "--profile"),
                new ApplicationProcessIdentity(Set.of("\"C:\\Apps\\.\\Studio.exe\""),
                        Set.of("C:\\Apps\\Root\\"), Set.of("STUDIO.EXE"), Set.of("HELPER.EXE"),
                        List.of(Set.of("--profile", "work")), List.of(List.of("--exact", "value")),
                        "HOST", List.of(new ApplicationWindowSignature("StudioWindow", "Studio.*")), true));
        ApplicationDefinition equivalent = new ApplicationDefinition(null, "studio", Set.of("editor", "ide"),
                List.of("c:/tools/launcher.exe", "--profile"),
                new ApplicationProcessIdentity(Set.of("c:/apps/studio.exe"),
                        Set.of("c:/apps/root"), Set.of("studio.exe"), Set.of("helper.exe"),
                        List.of(Set.of("work", "--profile")), List.of(List.of("--exact", "value")),
                        "host", List.of(new ApplicationWindowSignature("studiowindow", "Studio.*")), true));

        assertEquals(ApplicationCatalogIdentity.runtimeIdentityFingerprint(first),
                ApplicationCatalogIdentity.runtimeIdentityFingerprint(equivalent));
        assertEquals(ApplicationCatalogIdentity.definitionFingerprint(first),
                ApplicationCatalogIdentity.definitionFingerprint(equivalent));

        ApplicationDefinition spacedArgument = new ApplicationDefinition(null, "Studio", Set.of(),
                List.of("open"), new ApplicationProcessIdentity(Set.of(), Set.of("C:\\Apps"), Set.of(),
                Set.of(), List.of(Set.of(" --profile")), List.of(), "", List.of(), false));
        ApplicationDefinition exactArgument = new ApplicationDefinition(null, "Studio", Set.of(),
                List.of("open"), new ApplicationProcessIdentity(Set.of(), Set.of("C:\\Apps"), Set.of(),
                Set.of(), List.of(Set.of("--profile")), List.of(), "", List.of(), false));

        assertNotEquals(ApplicationCatalogIdentity.runtimeIdentityFingerprint(spacedArgument),
                ApplicationCatalogIdentity.runtimeIdentityFingerprint(exactArgument));
    }

    @Test
    void hostRelationshipShouldStoreTheCatalogAppIdCasing() throws Exception {
        Path config = temporaryDirectory.resolve("host-casing.json");
        Files.writeString(config, """
                {"hostRelationships":{"prime":"FIREFOX"}}
                """);
        ApplicationCatalog catalog = new ApplicationCatalog(() -> List.of(
                shared("Firefox", List.of()), shared("prime", List.of(Set.of("prime")))),
                new ApplicationAliasConfigLoader(config));

        assertTrue(catalog.refresh());
        ApplicationDefinition prime = catalog.applications().stream()
                .filter(application -> "prime".equals(application.getId())).findFirst().orElseThrow();
        assertEquals("Firefox", prime.getProcessIdentity().hostApplicationId());
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

    private ApplicationDefinition definition(String id, String name, Set<String> aliases, String path) {
        return new ApplicationDefinition(id, name, aliases, List.of("open"),
                new ApplicationProcessIdentity(Set.of(path), Set.of(), Set.of()));
    }

    private ApplicationDefinition definitionWithIdentity(ApplicationProcessIdentity identity) {
        return new ApplicationDefinition(null, "Studio", Set.of(), List.of("open"), identity);
    }
}

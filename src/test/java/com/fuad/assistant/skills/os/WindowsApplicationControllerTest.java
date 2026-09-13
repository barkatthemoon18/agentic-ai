package com.fuad.assistant.skills.os;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class WindowsApplicationControllerTest {
    @Test
    void shouldDistinguishBackgroundAndVisibleApplicationStates() {
        ApplicationDefinition application = exactApplication("spotify", "C:\\Apps\\Spotify.exe");
        FakeProcess process = new FakeProcess(10, "C:\\Apps\\Spotify.exe");
        FakeWindowService windows = new FakeWindowService();
        WindowsApplicationController controller = controller(windows, List.of(process), List.of(application));

        assertEquals(ApplicationRuntimeState.RUNNING_BACKGROUND,
                controller.runtimeState(application).runtimeState());

        windows.windows = List.of(new WindowService.WindowHandle(100, 10));
        assertEquals(ApplicationRuntimeState.RUNNING_WITH_WINDOW,
                controller.runtimeState(application).runtimeState());
    }

    @Test
    void shouldCloseWindowGracefullyBeforeDestroyingProcess() {
        ApplicationDefinition application = exactApplication("spotify", "C:\\Apps\\Spotify.exe");
        FakeProcess process = new FakeProcess(10, "C:\\Apps\\Spotify.exe");
        FakeWindowService windows = new FakeWindowService();
        windows.windows = List.of(new WindowService.WindowHandle(100, 10));
        windows.onClose = () -> process.alive = false;
        WindowsApplicationController controller = controller(windows, List.of(process), List.of(application));

        assertEquals(ApplicationActionResult.Status.SUCCESS, controller.closeDetailed(application).status());
        assertTrue(windows.closeCalled);
        assertFalse(process.destroyCalled);
    }

    @Test
    void shouldUseNormalProcessTerminationWhenNoWindowCanClose() {
        ApplicationDefinition application = exactApplication("spotify", "C:\\Apps\\Spotify.exe");
        FakeProcess process = new FakeProcess(10, "C:\\Apps\\Spotify.exe");
        WindowsApplicationController controller = controller(new FakeWindowService(), List.of(process),
                List.of(application));

        assertEquals(ApplicationActionResult.Status.SUCCESS, controller.closeDetailed(application).status());
        assertTrue(process.destroyCalled);
        assertFalse(process.forceCalled);
    }

    @Test
    void shouldReturnVerifiedFocusResultFromWindowService() {
        ApplicationDefinition application = exactApplication("spotify", "C:\\Apps\\Spotify.exe");
        FakeProcess process = new FakeProcess(10, "C:\\Apps\\Spotify.exe");
        FakeWindowService windows = new FakeWindowService();
        windows.windows = List.of(new WindowService.WindowHandle(100, 10));
        windows.focusResult = WindowService.FocusResult.REJECTED;
        WindowsApplicationController controller = controller(windows, List.of(process), List.of(application));

        assertEquals(ApplicationActionResult.Status.FOCUS_REJECTED, controller.focus(application).status());
    }

    @Test
    void processNameAloneShouldNeverAuthorizeRuntimeOperations() {
        ApplicationDefinition application = new ApplicationDefinition("firefox", "Firefox", Set.of(), List.of("open"),
                new ApplicationProcessIdentity(Set.of(), Set.of(), Set.of("firefox.exe")));
        FakeProcess process = new FakeProcess(10, "firefox.exe");
        FakeWindowService windows = new FakeWindowService();
        windows.windows = List.of(new WindowService.WindowHandle(100, 10));
        WindowsApplicationController controller = controller(windows, List.of(process), List.of(application));

        assertEquals(ApplicationActionResult.Status.PROCESS_IDENTITY_UNAVAILABLE,
                controller.closeDetailed(application).status());
        assertEquals(ApplicationActionResult.Status.PROCESS_IDENTITY_UNAVAILABLE,
                controller.focus(application).status());
        assertEquals(ApplicationActionResult.Status.PROCESS_IDENTITY_UNAVAILABLE,
                controller.runtimeState(application).status());
        assertFalse(windows.closeCalled);
        assertFalse(windows.foregroundCalled);
        assertFalse(process.destroyCalled);
    }

    @Test
    void commandLineSetWithoutAProcessLocatorShouldBeUnavailable() {
        ApplicationDefinition application = new ApplicationDefinition("orphan", "orphan", Set.of(), List.of("open"),
                new ApplicationProcessIdentity(Set.of(), Set.of(), Set.of(), Set.of(),
                        List.of(Set.of("--app-id=orphan"))));
        WindowsApplicationController controller = controller(new FakeWindowService(), List.of(),
                List.of(application));

        assertEquals(ApplicationActionResult.Status.PROCESS_IDENTITY_UNAVAILABLE,
                controller.runtimeState(application).status());
    }

    @Test
    void sharedExecutableWithoutDiscriminatorShouldFailClosedForEveryOperation() {
        ApplicationDefinition firefox = sharedApplication("firefox", Set.of());
        ApplicationDefinition primeVideo = sharedApplication("prime-video", Set.of());
        FakeProcess browser = new FakeProcess(10, "C:\\Mozilla\\firefox.exe");
        FakeProcess webApp = new FakeProcess(20, "C:\\Mozilla\\firefox.exe");
        FakeWindowService windows = new FakeWindowService();
        windows.windows = List.of(new WindowService.WindowHandle(100, 10),
                new WindowService.WindowHandle(200, 20));
        WindowsApplicationController controller = controller(windows, List.of(browser, webApp),
                List.of(firefox, primeVideo));

        assertEquals(ApplicationActionResult.Status.PROCESS_IDENTITY_UNAVAILABLE,
                controller.closeDetailed(primeVideo).status());
        assertEquals(ApplicationActionResult.Status.PROCESS_IDENTITY_UNAVAILABLE,
                controller.focus(primeVideo).status());
        assertEquals(ApplicationActionResult.Status.PROCESS_IDENTITY_UNAVAILABLE,
                controller.runtimeState(primeVideo).status());
        assertFalse(browser.destroyCalled);
        assertFalse(webApp.destroyCalled);
        assertFalse(windows.closeCalled);
        assertFalse(windows.foregroundCalled);
    }

    @Test
    void sharedExecutableWithExclusiveRuntimeArgumentsShouldTargetOnlyRequestedApplication() {
        ApplicationDefinition firefox = sharedApplication("firefox", Set.of("--browser"));
        ApplicationDefinition primeVideo = sharedApplication("prime-video", Set.of("--app-id=prime"));
        FakeProcess browser = new FakeProcess(10, "C:\\Mozilla\\firefox.exe", "--browser");
        FakeProcess webApp = new FakeProcess(20, "C:\\Mozilla\\firefox.exe", "--app-id=prime");
        FakeWindowService windows = new FakeWindowService();
        windows.windows = List.of(new WindowService.WindowHandle(100, 10),
                new WindowService.WindowHandle(200, 20));
        windows.onClose = () -> webApp.alive = false;
        WindowsApplicationController controller = controller(windows, List.of(browser, webApp),
                List.of(firefox, primeVideo));

        assertEquals(ApplicationActionResult.Status.SUCCESS, controller.closeDetailed(primeVideo).status());
        assertEquals(Set.of(20L), windows.lastRequestedPids);
        assertEquals(List.of(new WindowService.WindowHandle(200, 20)), windows.closedWindows);
        assertTrue(browser.alive);
        assertFalse(browser.destroyCalled);
    }

    @Test
    void runtimeArgumentsCompatibleWithTwoDefinitionsShouldBeUnverifiable() {
        ApplicationDefinition first = sharedApplication("first", Set.of("--app-id=first"));
        ApplicationDefinition second = sharedApplication("second", Set.of("--profile=work"));
        FakeProcess process = new FakeProcess(10, "C:\\Mozilla\\firefox.exe",
                "--app-id=first", "--profile=work");
        WindowsApplicationController controller = controller(new FakeWindowService(), List.of(process),
                List.of(first, second));

        assertEquals(ApplicationActionResult.Status.PROCESS_IDENTITY_UNAVAILABLE,
                controller.runtimeState(first).status());
    }

    @Test
    void staticallyOverlappingArgumentSetsShouldNotBecomeStrongIdentity() {
        ApplicationDefinition first = sharedApplication("first", Set.of("--app-id=first"));
        ApplicationDefinition second = sharedApplication("second",
                Set.of("--app-id=first", "--profile=work"));
        FakeProcess process = new FakeProcess(10, "C:\\Mozilla\\firefox.exe", "--app-id=first");
        WindowsApplicationController controller = controller(new FakeWindowService(), List.of(process),
                List.of(first, second));

        assertEquals(ApplicationActionResult.Status.PROCESS_IDENTITY_UNAVAILABLE,
                controller.runtimeState(first).status());
    }

    @Test
    void shouldNotUseUnsafeAlternativeBecauseAnotherTargetSignatureIsExclusive() {
        ApplicationDefinition first = new ApplicationDefinition("first", "first", Set.of(), List.of("open"),
                new ApplicationProcessIdentity(Set.of("C:\\Mozilla\\firefox.exe"), Set.of(),
                        Set.of("firefox.exe"), Set.of(),
                        List.of(Set.of("--shared"), Set.of("--exclusive-first"))));
        ApplicationDefinition second = sharedApplication("second", Set.of("--shared", "--second"));
        FakeProcess process = new FakeProcess(10, "C:\\Mozilla\\firefox.exe", "--shared");
        WindowsApplicationController controller = controller(new FakeWindowService(), List.of(process),
                List.of(first, second));

        assertEquals(ApplicationActionResult.Status.PROCESS_IDENTITY_UNAVAILABLE,
                controller.runtimeState(first).status());
    }

    @Test
    void missingRuntimeArgumentsForSharedHostShouldBeUnverifiable() {
        ApplicationDefinition firefox = sharedApplication("firefox", Set.of("--browser"));
        ApplicationDefinition primeVideo = sharedApplication("prime", Set.of("--app-id=prime"));
        FakeProcess process = new FakeProcess(10, "C:\\Mozilla\\firefox.exe", "--app-id=prime");
        process.arguments = null;
        WindowsApplicationController controller = controller(new FakeWindowService(), List.of(process),
                List.of(firefox, primeVideo));

        assertEquals(ApplicationActionResult.Status.PROCESS_IDENTITY_UNAVAILABLE,
                controller.runtimeState(primeVideo).status());
    }

    @Test
    void trustedProcessNameShouldApplyOnlyWhenPathIsUnavailable() {
        ApplicationDefinition trusted = new ApplicationDefinition("trusted", "trusted", Set.of(), List.of("open"),
                new ApplicationProcessIdentity(Set.of(), Set.of(), Set.of(), Set.of("trusted.exe"), List.of()));
        FakeProcess basenameOnly = new FakeProcess(10, "trusted.exe");
        WindowsApplicationController basenameController = controller(new FakeWindowService(), List.of(basenameOnly),
                List.of(trusted));
        FakeProcess fullPath = new FakeProcess(20, "C:\\Unexpected\\trusted.exe");
        WindowsApplicationController pathController = controller(new FakeWindowService(), List.of(fullPath),
                List.of(trusted));

        assertEquals(ApplicationRuntimeState.RUNNING_BACKGROUND,
                basenameController.runtimeState(trusted).runtimeState());
        assertEquals(ApplicationActionResult.Status.PROCESS_IDENTITY_UNAVAILABLE,
                pathController.runtimeState(trusted).status());
    }

    @Test
    void uniquePackageRootShouldProvideStrongIdentity() {
        ApplicationDefinition packaged = new ApplicationDefinition("packaged", "packaged", Set.of(), List.of("open"),
                new ApplicationProcessIdentity(Set.of(), Set.of("C:\\Packages\\Unique"), Set.of()));
        FakeProcess process = new FakeProcess(10, "C:\\Packages\\Unique\\app.exe");
        WindowsApplicationController controller = controller(new FakeWindowService(), List.of(process),
                List.of(packaged));

        assertEquals(ApplicationRuntimeState.RUNNING_BACKGROUND,
                controller.runtimeState(packaged).runtimeState());
    }

    @Test
    void overlappingPackageRootsShouldBeUnverifiable() {
        ApplicationDefinition broad = new ApplicationDefinition("broad", "broad", Set.of(), List.of("open"),
                new ApplicationProcessIdentity(Set.of(), Set.of("C:\\Packages"), Set.of()));
        ApplicationDefinition nested = new ApplicationDefinition("nested", "nested", Set.of(), List.of("open"),
                new ApplicationProcessIdentity(Set.of(), Set.of("C:\\Packages\\Nested"), Set.of()));
        FakeProcess process = new FakeProcess(10, "C:\\Packages\\Nested\\app.exe");
        WindowsApplicationController controller = controller(new FakeWindowService(), List.of(process),
                List.of(broad, nested));

        assertEquals(ApplicationActionResult.Status.PROCESS_IDENTITY_UNAVAILABLE,
                controller.runtimeState(broad).status());
        assertEquals(ApplicationActionResult.Status.PROCESS_IDENTITY_UNAVAILABLE,
                controller.runtimeState(nested).status());
    }

    @Test
    void commandLineArgumentsShouldMatchWholeTokens() {
        ApplicationDefinition browser = sharedApplication("browser", Set.of("--browser"));
        ApplicationDefinition primeVideo = sharedApplication("prime", Set.of("--app-id=prime"));
        FakeProcess process = new FakeProcess(10, "C:\\Mozilla\\firefox.exe", "--app-id=prime-clone");
        WindowsApplicationController controller = controller(new FakeWindowService(), List.of(process),
                List.of(browser, primeVideo));

        assertEquals(ApplicationRuntimeState.NOT_RUNNING,
                controller.runtimeState(primeVideo).runtimeState());
    }

    @Test
    void shouldResolveExecutableAndArgumentsFromCommandLineWhenSeparateFieldsAreUnavailable() {
        ApplicationDefinition browser = sharedApplication("browser", Set.of("--browser"));
        ApplicationDefinition primeVideo = sharedApplication("prime", Set.of("--app-id=prime"));
        FakeProcess process = new FakeProcess(10, null);
        process.arguments = null;
        process.commandLine = "\"C:\\Mozilla\\firefox.exe\" --app-id=prime";
        WindowsApplicationController controller = controller(new FakeWindowService(), List.of(process),
                List.of(browser, primeVideo));

        assertEquals(ApplicationRuntimeState.RUNNING_BACKGROUND,
                controller.runtimeState(primeVideo).runtimeState());
    }

    @Test
    void contradictoryFullPathShouldBeNoMatchInsteadOfGlobalLimitation() {
        ApplicationDefinition application = exactApplication("spotify", "C:\\Apps\\Spotify.exe");
        FakeProcess unrelated = new FakeProcess(10, "C:\\Other\\Spotify.exe");
        WindowsApplicationController controller = controller(new FakeWindowService(), List.of(unrelated),
                List.of(application));

        ApplicationActionResult result = controller.runtimeState(application);

        assertEquals(ApplicationActionResult.Status.SUCCESS, result.status());
        assertEquals(ApplicationRuntimeState.NOT_RUNNING, result.runtimeState());
    }

    @Test
    void basenameCandidateWithoutExposedPathShouldRemainUnverifiable() {
        ApplicationDefinition application = exactApplication("spotify", "C:\\Apps\\Spotify.exe");
        FakeProcess candidate = new FakeProcess(10, "Spotify.exe");
        WindowsApplicationController controller = controller(new FakeWindowService(), List.of(candidate),
                List.of(application));

        assertEquals(ApplicationActionResult.Status.PROCESS_IDENTITY_UNAVAILABLE,
                controller.runtimeState(application).status());
    }

    @Test
    void sameBasenameAtDifferentExactPathsShouldRemainDistinguishable() {
        ApplicationDefinition first = exactApplication("electron-one", "C:\\One\\electron.exe");
        ApplicationDefinition second = exactApplication("electron-two", "C:\\Two\\electron.exe");
        FakeProcess firstProcess = new FakeProcess(10, "C:\\One\\electron.exe");
        FakeProcess secondProcess = new FakeProcess(20, "C:\\Two\\electron.exe");
        FakeWindowService windows = new FakeWindowService();
        windows.windows = List.of(new WindowService.WindowHandle(100, 10));
        WindowsApplicationController controller = controller(windows, List.of(firstProcess, secondProcess),
                List.of(first, second));

        assertEquals(ApplicationRuntimeState.RUNNING_WITH_WINDOW,
                controller.runtimeState(first).runtimeState());
        assertEquals(Set.of(10L), windows.lastRequestedPids);
    }

    @Test
    void shouldRevalidateImmediatelyBeforeFocusSideEffect() {
        ApplicationDefinition application = exactApplication("spotify", "C:\\Apps\\Spotify.exe");
        FakeProcess process = new FakeProcess(10, "C:\\Apps\\Spotify.exe");
        FakeWindowService windows = new FakeWindowService();
        windows.windows = List.of(new WindowService.WindowHandle(100, 10));
        windows.beforeFocusValidation = () -> process.command = "C:\\Other\\Spotify.exe";
        WindowsApplicationController controller = controller(windows, List.of(process), List.of(application));

        assertEquals(ApplicationActionResult.Status.PROCESS_IDENTITY_UNAVAILABLE,
                controller.focus(application).status());
        assertFalse(windows.foregroundCalled);
    }

    @Test
    void shouldRevalidateAgainAfterRestoreAndBeforeSetForegroundWindow() {
        ApplicationDefinition application = exactApplication("spotify", "C:\\Apps\\Spotify.exe");
        FakeProcess process = new FakeProcess(10, "C:\\Apps\\Spotify.exe");
        FakeWindowService windows = new FakeWindowService();
        windows.windows = List.of(new WindowService.WindowHandle(100, 10));
        windows.betweenFocusValidations = () -> process.command = "C:\\Other\\Spotify.exe";
        WindowsApplicationController controller = controller(windows, List.of(process), List.of(application));

        assertEquals(ApplicationActionResult.Status.PROCESS_IDENTITY_UNAVAILABLE,
                controller.focus(application).status());
        assertTrue(windows.restoreCalled);
        assertFalse(windows.foregroundCalled);
    }

    @Test
    void shouldRevalidateImmediatelyBeforeWmCloseBatch() {
        ApplicationDefinition application = exactApplication("spotify", "C:\\Apps\\Spotify.exe");
        FakeProcess process = new FakeProcess(10, "C:\\Apps\\Spotify.exe");
        FakeWindowService windows = new FakeWindowService();
        windows.windows = List.of(new WindowService.WindowHandle(100, 10));
        windows.beforeCloseValidation = () -> process.command = "C:\\Other\\Spotify.exe";
        WindowsApplicationController controller = controller(windows, List.of(process), List.of(application));

        assertEquals(ApplicationActionResult.Status.PROCESS_IDENTITY_UNAVAILABLE,
                controller.closeDetailed(application).status());
        assertFalse(windows.closeCalled);
        assertFalse(process.destroyCalled);
    }

    @Test
    void shouldRevalidateAgainBeforeDestroyFallback() {
        ApplicationDefinition application = exactApplication("spotify", "C:\\Apps\\Spotify.exe");
        FakeProcess process = new FakeProcess(10, "C:\\Apps\\Spotify.exe");
        FakeWindowService windows = new FakeWindowService();
        windows.onVisibleWindows = () -> process.command = "C:\\Other\\Spotify.exe";
        WindowsApplicationController controller = controller(windows, List.of(process), List.of(application));

        assertEquals(ApplicationActionResult.Status.PROCESS_IDENTITY_UNAVAILABLE,
                controller.closeDetailed(application).status());
        assertFalse(process.destroyCalled);
    }

    @Test
    void failedInitialObservationShouldNeverBeReportedAsNotRunning() {
        ApplicationDefinition application = exactApplication("spotify", "C:\\Apps\\Spotify.exe");
        FakeSnapshotSource source = new FakeSnapshotSource();
        source.batch = ProcessSnapshotBatch.failed(ProcessObservationFailure.TIMEOUT, "slow WMI");
        WindowsApplicationController controller = snapshotController(new FakeWindowService(), source,
                List.of(), List.of(application));

        assertEquals(ApplicationActionResult.Status.PROCESS_IDENTITY_UNAVAILABLE,
                controller.runtimeState(application).status());
    }

    @Test
    void failedPidLookupShouldCancelCloseInsteadOfPretendingTheProcessDisappeared() {
        ApplicationDefinition application = exactApplication("spotify", "C:\\Apps\\Spotify.exe");
        FakeProcess process = new FakeProcess(10, "C:\\Apps\\Spotify.exe");
        FakeSnapshotSource source = new FakeSnapshotSource();
        source.batch = ProcessSnapshotBatch.complete(List.of(snapshot(10, "2026-01-01T00:00:00Z",
                "C:\\Apps\\Spotify.exe", "Spotify.exe", List.of())));
        source.lookup = ProcessSnapshotLookup.failed(ProcessObservationFailure.ACCESS_DENIED, "denied");
        WindowsApplicationController controller = snapshotController(new FakeWindowService(), source,
                List.of(process), List.of(application));

        assertEquals(ApplicationActionResult.Status.PROCESS_IDENTITY_UNAVAILABLE,
                controller.closeDetailed(application).status());
        assertFalse(process.destroyCalled);
    }

    @Test
    void onlyNotFoundShouldConfirmThatTheOriginalProcessIsGone() {
        ApplicationDefinition application = exactApplication("spotify", "C:\\Apps\\Spotify.exe");
        FakeProcess process = new FakeProcess(10, "C:\\Apps\\Spotify.exe");
        FakeSnapshotSource source = new FakeSnapshotSource();
        source.batch = ProcessSnapshotBatch.complete(List.of(snapshot(10, "2026-01-01T00:00:00Z",
                "C:\\Apps\\Spotify.exe", "Spotify.exe", List.of())));
        source.lookup = ProcessSnapshotLookup.notFound();
        WindowsApplicationController controller = snapshotController(new FakeWindowService(), source,
                List.of(process), List.of(application));

        assertEquals(ApplicationActionResult.Status.SUCCESS, controller.closeDetailed(application).status());
        assertFalse(process.destroyCalled);
    }

    @Test
    void reusedPidShouldCancelCloseWhenWmiCreationTimeChanges() {
        ApplicationDefinition application = exactApplication("spotify", "C:\\Apps\\Spotify.exe");
        FakeProcess process = new FakeProcess(10, "C:\\Apps\\Spotify.exe");
        FakeSnapshotSource source = new FakeSnapshotSource();
        source.batch = ProcessSnapshotBatch.complete(List.of(snapshot(10, "2026-01-01T00:00:00Z",
                "C:\\Apps\\Spotify.exe", "Spotify.exe", List.of())));
        source.lookup = ProcessSnapshotLookup.found(snapshot(10, "2026-01-01T00:00:01Z",
                "C:\\Apps\\Spotify.exe", "Spotify.exe", List.of()));
        WindowsApplicationController controller = snapshotController(new FakeWindowService(), source,
                List.of(process), List.of(application));

        assertEquals(ApplicationActionResult.Status.PROCESS_IDENTITY_UNAVAILABLE,
                controller.closeDetailed(application).status());
        assertFalse(process.destroyCalled);
    }

    @Test
    void wmiArgumentsShouldIdentifyASharedHostWebApplication() {
        ApplicationDefinition firefox = sharedApplication("firefox", Set.of());
        ApplicationDefinition primeVideo = sharedApplication("prime-video",
                Set.of("-new-window", "https://www.primevideo.com"));
        FakeSnapshotSource source = new FakeSnapshotSource();
        source.batch = ProcessSnapshotBatch.complete(List.of(snapshot(10, "2026-01-01T00:00:00Z",
                "C:\\Mozilla\\firefox.exe", "firefox.exe",
                List.of("-new-window", "https://www.primevideo.com"))));
        FakeWindowService windows = new FakeWindowService();
        windows.windows = List.of(new WindowService.WindowHandle(100, 10));
        WindowsApplicationController controller = snapshotController(windows, source,
                List.of(new FakeProcess(10, "C:\\Mozilla\\firefox.exe")), List.of(firefox, primeVideo));

        assertEquals(ApplicationRuntimeState.RUNNING_WITH_WINDOW,
                controller.runtimeState(primeVideo).runtimeState());
    }

    @Test
    void exactArgumentSignaturesShouldCompareTheOrderedVectorAndKeepEmptyLiteral() {
        ApplicationDefinition firefox = hostApplication(List.of(List.of(), List.of("-os-autostart")), true);
        ApplicationDefinition prime = hostedApplication(false);
        FakeWindowService windows = new FakeWindowService();
        windows.windows = List.of(new WindowService.WindowHandle(100, 10,
                "A page — Mozilla Firefox", "MozillaWindowClass"));
        FakeSnapshotSource source = new FakeSnapshotSource();
        source.batch = ProcessSnapshotBatch.complete(List.of(snapshot(10, "2026-01-01T00:00:00Z",
                "C:\\Mozilla\\firefox.exe", "firefox.exe", List.of())));
        WindowsApplicationController controller = snapshotController(windows, source,
                List.of(new FakeProcess(10, "C:\\Mozilla\\firefox.exe")), List.of(firefox, prime));

        assertEquals(ApplicationRuntimeState.RUNNING_WITH_WINDOW,
                controller.runtimeState(firefox).runtimeState());

        source.batch = ProcessSnapshotBatch.complete(List.of(snapshot(10, "2026-01-01T00:00:00Z",
                "C:\\Mozilla\\firefox.exe", "firefox.exe", List.of("--other"))));
        assertEquals(ApplicationRuntimeState.NOT_RUNNING,
                controller.runtimeState(firefox).runtimeState());
    }

    @Test
    void exactArgumentSignaturesShouldRejectReorderedVectors() {
        ApplicationDefinition firefox = hostApplication(List.of(List.of("-profile", "work")), true);
        ApplicationDefinition prime = hostedApplication(false);
        FakeSnapshotSource source = new FakeSnapshotSource();
        source.batch = ProcessSnapshotBatch.complete(List.of(snapshot(10, "2026-01-01T00:00:00Z",
                "C:\\Mozilla\\firefox.exe", "firefox.exe", List.of("work", "-profile"))));
        WindowsApplicationController controller = snapshotController(new FakeWindowService(), source,
                List.of(new FakeProcess(10, "C:\\Mozilla\\firefox.exe")), List.of(firefox, prime));

        assertEquals(ApplicationRuntimeState.NOT_RUNNING,
                controller.runtimeState(firefox).runtimeState());
    }

    @Test
    void windowSignatureShouldNeverReplaceStrongHostProcessIdentity() {
        ApplicationDefinition firefox = hostApplication(List.of(), true);
        ApplicationDefinition prime = hostedApplication(true);
        FakeWindowService windows = new FakeWindowService();
        windows.windows = List.of(new WindowService.WindowHandle(100, 10,
                "Prime Video", "MozillaWindowClass"));
        FakeSnapshotSource source = new FakeSnapshotSource();
        source.batch = ProcessSnapshotBatch.complete(List.of(snapshot(10, "2026-01-01T00:00:00Z",
                "C:\\Mozilla\\firefox.exe", "firefox.exe", List.of("-os-autostart"))));
        WindowsApplicationController controller = snapshotController(windows, source,
                List.of(new FakeProcess(10, "C:\\Mozilla\\firefox.exe")), List.of(firefox, prime));

        assertEquals(ApplicationActionResult.Status.PROCESS_IDENTITY_UNAVAILABLE,
                controller.runtimeState(prime).status());
    }

    @Test
    void hostedWindowCloseShouldNeverTerminateTheVerifiedSharedHost() {
        ApplicationDefinition firefox = hostApplication(List.of(List.of("-os-autostart")), false);
        ApplicationDefinition prime = hostedApplication(true);
        FakeProcess hostProcess = new FakeProcess(10, "C:\\Mozilla\\firefox.exe");
        WindowsProcessSnapshot observed = snapshot(10, "2026-01-01T00:00:00Z",
                "C:\\Mozilla\\firefox.exe", "firefox.exe", List.of("-os-autostart"));
        FakeSnapshotSource source = new FakeSnapshotSource();
        source.batch = ProcessSnapshotBatch.complete(List.of(observed));
        source.lookup = ProcessSnapshotLookup.found(observed);
        FakeWindowService windows = new FakeWindowService();
        windows.windows = List.of(new WindowService.WindowHandle(100, 10,
                "Prime Video", "MozillaWindowClass"));
        windows.onClose = () -> windows.windows = List.of();
        WindowsApplicationController controller = snapshotController(windows, source,
                List.of(hostProcess), List.of(firefox, prime));

        assertEquals(ApplicationActionResult.Status.SUCCESS, controller.closeDetailed(prime).status());
        assertTrue(windows.closeCalled);
        assertFalse(hostProcess.destroyCalled);
        assertTrue(hostProcess.isAlive());
    }

    @Test
    void runningApplicationListingShouldUseOneProcessAndOneWindowSnapshot() {
        ApplicationDefinition first = exactApplication("first", "C:\\Apps\\First.exe");
        ApplicationDefinition second = exactApplication("second", "C:\\Apps\\Second.exe");
        ApplicationDefinition weak = new ApplicationDefinition("weak", "weak", Set.of(), List.of("open"),
                new ApplicationProcessIdentity(Set.of(), Set.of(), Set.of("Weak.exe")));
        FakeSnapshotSource source = new FakeSnapshotSource();
        source.batch = ProcessSnapshotBatch.complete(List.of(
                snapshot(10, "2026-01-01T00:00:00Z", "C:\\Apps\\First.exe", "First.exe", List.of()),
                snapshot(20, "2026-01-01T00:00:01Z", "C:\\Apps\\Second.exe", "Second.exe", List.of()),
                snapshot(30, "2026-01-01T00:00:02Z", "C:\\Apps\\Weak.exe", "Weak.exe", List.of())));
        FakeWindowService windows = new FakeWindowService();
        windows.windows = List.of(new WindowService.WindowHandle(100, 10),
                new WindowService.WindowHandle(200, 20));
        WindowsApplicationController controller = snapshotController(windows, source,
                List.of(new FakeProcess(10, "C:\\Apps\\First.exe"),
                        new FakeProcess(20, "C:\\Apps\\Second.exe")), List.of(first, second, weak));

        OpenApplicationsResult result = controller.runningApplications();

        assertEquals(OpenApplicationsResult.Status.SUCCESS, result.status());
        assertEquals(List.of("first", "second"), result.applications().stream()
                .map(entry -> entry.application().getId()).toList());
        assertEquals(1, result.unverifiableCount());
        assertEquals(1, source.snapshotAllCalls);
        assertEquals(1, windows.visibleCalls);
    }

    private WindowsApplicationController controller(FakeWindowService windows, List<ProcessHandle> processes,
                                                    List<ApplicationDefinition> definitions) {
        return new WindowsApplicationController(windows, () -> processes, () -> definitions, Duration.ZERO);
    }

    private WindowsApplicationController snapshotController(FakeWindowService windows,
                                                            WindowsProcessSnapshotSource source,
                                                            List<? extends ProcessHandle> processes,
                                                            List<ApplicationDefinition> definitions) {
        return new WindowsApplicationController(windows, source,
                pid -> processes.stream().filter(process -> process.pid() == pid)
                        .map(process -> (ProcessHandle) process).findFirst(),
                () -> definitions, Duration.ZERO);
    }

    private WindowsProcessSnapshot snapshot(long pid, String created, String path, String name,
                                            List<String> arguments) {
        return new WindowsProcessSnapshot(pid, Optional.of(Instant.parse(created)),
                Optional.of(java.nio.file.Path.of(path)), name, arguments, true);
    }

    private ApplicationDefinition exactApplication(String id, String path) {
        return new ApplicationDefinition(id, id, Set.of(), List.of("open"),
                new ApplicationProcessIdentity(Set.of(path), Set.of(), Set.of()));
    }

    private ApplicationDefinition sharedApplication(String id, Set<String> arguments) {
        List<Set<String>> argumentSets = arguments.isEmpty() ? List.of() : List.of(arguments);
        return new ApplicationDefinition(id, id, Set.of(), List.of("open"),
                new ApplicationProcessIdentity(Set.of("C:\\Mozilla\\firefox.exe"), Set.of(),
                        Set.of("firefox.exe"), Set.of(), argumentSets));
    }

    private ApplicationDefinition hostApplication(List<List<String>> exactArguments,
                                                  boolean windowAssociationEnabled) {
        return new ApplicationDefinition("firefox", "Firefox", Set.of(), List.of("open"),
                new ApplicationProcessIdentity(Set.of("C:\\Mozilla\\firefox.exe"), Set.of(),
                        Set.of("firefox.exe"), Set.of(), List.of(), exactArguments, "",
                        windowAssociationEnabled ? List.of(new ApplicationWindowSignature(
                                "MozillaWindowClass", ".*Mozilla Firefox$")) : List.of(),
                        windowAssociationEnabled));
    }

    private ApplicationDefinition hostedApplication(boolean windowAssociationEnabled) {
        return new ApplicationDefinition("prime", "Prime Video", Set.of(), List.of("open"),
                new ApplicationProcessIdentity(Set.of("C:\\Mozilla\\firefox.exe"), Set.of(),
                        Set.of("firefox.exe"), Set.of(), List.of(Set.of("-taskbar-tab", "prime")),
                        List.of(), "firefox",
                        windowAssociationEnabled ? List.of(new ApplicationWindowSignature(
                                "MozillaWindowClass", "Prime Video")) : List.of(),
                        windowAssociationEnabled));
    }

    private static final class FakeWindowService implements WindowService {
        private List<WindowHandle> windows = List.of();
        private FocusResult focusResult = FocusResult.FOCUSED;
        private boolean closeCalled;
        private boolean restoreCalled;
        private boolean foregroundCalled;
        private List<WindowHandle> closedWindows = List.of();
        private Set<Long> lastRequestedPids = Set.of();
        private Runnable onVisibleWindows = () -> { };
        private Runnable beforeCloseValidation = () -> { };
        private Runnable beforeFocusValidation = () -> { };
        private Runnable betweenFocusValidations = () -> { };
        private Runnable onClose = () -> { };
        private int visibleCalls;

        @Override
        public List<WindowHandle> visibleWindows(Set<Long> processIds) {
            visibleCalls++;
            lastRequestedPids = Set.copyOf(processIds);
            onVisibleWindows.run();
            return windows.stream().filter(window -> processIds.contains(window.processId())).toList();
        }

        @Override
        public boolean exists(WindowHandle window) {
            return windows.stream().anyMatch(current -> current.nativeHandle() == window.nativeHandle()
                    && current.processId() == window.processId());
        }

        @Override
        public CloseResult close(List<WindowHandle> windows, IdentityGuard guard) {
            beforeCloseValidation.run();
            if (!guard.isValid()) return CloseResult.IDENTITY_CHANGED;
            closeCalled = true;
            closedWindows = List.copyOf(windows);
            onClose.run();
            return CloseResult.SENT;
        }

        @Override
        public FocusResult focus(WindowHandle window, IdentityGuard guard) {
            beforeFocusValidation.run();
            if (!guard.isValid()) return FocusResult.IDENTITY_CHANGED;
            restoreCalled = true;
            betweenFocusValidations.run();
            if (!guard.isValid()) return FocusResult.IDENTITY_CHANGED;
            foregroundCalled = true;
            return focusResult;
        }
    }

    private static final class FakeSnapshotSource implements WindowsProcessSnapshotSource {
        private ProcessSnapshotBatch batch = ProcessSnapshotBatch.complete(List.of());
        private ProcessSnapshotLookup lookup = ProcessSnapshotLookup.notFound();
        private int snapshotAllCalls;

        @Override public ProcessSnapshotBatch snapshotAll() { snapshotAllCalls++; return batch; }
        @Override public ProcessSnapshotLookup snapshot(long pid) { return lookup; }
    }

    private static final class FakeProcess implements ProcessHandle {
        private final long pid;
        private final Instant started = Instant.parse("2026-01-01T00:00:00Z");
        private String command;
        private String commandLine;
        private String[] arguments;
        private boolean alive = true;
        private boolean destroyCalled;
        private boolean forceCalled;

        private FakeProcess(long pid, String command, String... arguments) {
            this.pid = pid;
            this.command = command;
            this.arguments = arguments;
        }

        @Override public long pid() { return pid; }
        @Override public Optional<ProcessHandle> parent() { return Optional.empty(); }
        @Override public Stream<ProcessHandle> children() { return Stream.empty(); }
        @Override public Stream<ProcessHandle> descendants() { return Stream.empty(); }
        @Override public Info info() { return new FakeInfo(command, commandLine, arguments, started); }
        @Override public CompletableFuture<ProcessHandle> onExit() { return new CompletableFuture<>(); }
        @Override public boolean supportsNormalTermination() { return true; }
        @Override public boolean destroy() { destroyCalled = true; alive = false; return true; }
        @Override public boolean destroyForcibly() { forceCalled = true; alive = false; return true; }
        @Override public boolean isAlive() { return alive; }
        @Override public int compareTo(ProcessHandle other) { return Long.compare(pid, other.pid()); }
    }

    private record FakeInfo(String executable, String line, String[] values, Instant started)
            implements ProcessHandle.Info {
        @Override public Optional<String> command() { return Optional.ofNullable(executable); }
        @Override public Optional<String> commandLine() { return Optional.ofNullable(line); }
        @Override public Optional<String[]> arguments() { return Optional.ofNullable(values); }
        @Override public Optional<Instant> startInstant() { return Optional.of(started); }
        @Override public Optional<Duration> totalCpuDuration() { return Optional.empty(); }
        @Override public Optional<String> user() { return Optional.empty(); }
    }
}

package com.fuad.assistant.skills.os;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.LongFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class WindowsApplicationController implements ApplicationController {
    private static final Duration DEFAULT_CLOSE_TIMEOUT = Duration.ofSeconds(2);
    private final WindowService windowService;
    private final ApplicationRuntimeResolver runtimeResolver;
    private final LongFunction<Optional<ProcessHandle>> processHandles;
    private final Supplier<List<ApplicationDefinition>> definitions;
    private final Duration closeTimeout;

    /** Creates a fail-closed controller. Runtime operations require a catalog-aware constructor. */
    public WindowsApplicationController() {
        this(List::of);
    }

    public WindowsApplicationController(Supplier<List<ApplicationDefinition>> definitions) {
        this(new JnaWindowService(), new WmiWindowsProcessSnapshotSource(), ProcessHandle::of,
                definitions, DEFAULT_CLOSE_TIMEOUT);
    }

    WindowsApplicationController(WindowService windowService, Supplier<List<ProcessHandle>> processes,
                                 Supplier<List<ApplicationDefinition>> definitions) {
        this(windowService, processes, definitions, DEFAULT_CLOSE_TIMEOUT);
    }

    WindowsApplicationController(WindowService windowService, Supplier<List<ProcessHandle>> processes,
                                 Supplier<List<ApplicationDefinition>> definitions, Duration closeTimeout) {
        this(windowService, new ProcessHandleSnapshotSource(processes), pid -> processes.get().stream()
                        .filter(process -> process.pid() == pid).findFirst(),
                definitions, closeTimeout);
    }

    WindowsApplicationController(WindowService windowService, WindowsProcessSnapshotSource snapshots,
                                 LongFunction<Optional<ProcessHandle>> processHandles,
                                 Supplier<List<ApplicationDefinition>> definitions, Duration closeTimeout) {
        this.windowService = windowService;
        this.definitions = definitions;
        this.runtimeResolver = new ApplicationRuntimeResolver(definitions, snapshots);
        this.processHandles = processHandles;
        this.closeTimeout = closeTimeout;
    }

    @Override
    public boolean open(ApplicationDefinition applicationDefinition) throws IOException {
        new ProcessBuilder(applicationDefinition.getOpenCommand()).start();
        return true;
    }

    @Override
    public boolean close(ApplicationDefinition applicationDefinition) {
        return closeDetailed(applicationDefinition).status() == ApplicationActionResult.Status.SUCCESS;
    }

    @Override
    public ApplicationActionResult closeDetailed(ApplicationDefinition applicationDefinition) {
        TargetResolution target = resolveTarget(applicationDefinition);
        ApplicationActionResult terminal = terminalResult(target);
        if (terminal != null) return terminal;

        if (target.windowScoped()) return closeWindowScoped(target);

        List<ApplicationRuntimeResolver.VerifiedProcess> matches = target.processes();
        Set<Long> pids = matches.stream().map(ApplicationRuntimeResolver.VerifiedProcess::pid)
                .collect(Collectors.toSet());
        Map<Long, ApplicationRuntimeResolver.VerifiedProcess> byPid = matches.stream()
                .collect(Collectors.toMap(ApplicationRuntimeResolver.VerifiedProcess::pid, process -> process));
        Map<Long, List<WindowService.WindowHandle>> windowsByPid = target.windows().stream()
                .collect(Collectors.groupingBy(WindowService.WindowHandle::processId));

        boolean closeMessageSent = false;
        for (Map.Entry<Long, List<WindowService.WindowHandle>> entry : windowsByPid.entrySet()) {
            ApplicationRuntimeResolver.VerifiedProcess verified = byPid.get(entry.getKey());
            if (verified == null) continue;
            WindowService.CloseResult closeResult = windowService.close(entry.getValue(),
                    () -> valid(target.identityApplication(), verified));
            if (closeResult == WindowService.CloseResult.IDENTITY_CHANGED) return identityUnavailable();
            if (closeResult == WindowService.CloseResult.REJECTED) {
                return ApplicationActionResult.failed("Windows rejected WM_CLOSE");
            }
            closeMessageSent = true;
        }
        if (closeMessageSent) waitForExit(availableHandles(matches), closeTimeout);

        List<ProcessHandle> destroyed = new ArrayList<>();
        for (ApplicationRuntimeResolver.VerifiedProcess verified : matches) {
            ApplicationRuntimeResolver.Revalidation current =
                    runtimeResolver.revalidate(target.identityApplication(), verified);
            if (current.status() == ApplicationRuntimeResolver.Revalidation.Status.GONE) continue;
            if (current.status() != ApplicationRuntimeResolver.Revalidation.Status.VALID) return identityUnavailable();
            Optional<ProcessHandle> currentHandle = processHandle(verified.pid());
            if (currentHandle.isEmpty() || !currentHandle.get().isAlive()) {
                ApplicationRuntimeResolver.Revalidation confirmation =
                        runtimeResolver.revalidate(target.identityApplication(), verified);
                if (confirmation.status() == ApplicationRuntimeResolver.Revalidation.Status.GONE) continue;
                return identityUnavailable();
            }
            ProcessHandle process = currentHandle.get();
            process.destroy();
            destroyed.add(process);
        }
        waitForExit(destroyed, closeTimeout);
        return terminationResult(target.identityApplication(), matches);
    }

    private ApplicationActionResult closeWindowScoped(TargetResolution target) {
        if (target.windows().isEmpty()) return ApplicationActionResult.of(ApplicationActionResult.Status.NOT_RUNNING);
        Map<Long, ApplicationRuntimeResolver.VerifiedProcess> byPid = target.processes().stream()
                .collect(Collectors.toMap(ApplicationRuntimeResolver.VerifiedProcess::pid, process -> process));
        for (Map.Entry<Long, List<WindowService.WindowHandle>> entry : target.windows().stream()
                .collect(Collectors.groupingBy(WindowService.WindowHandle::processId)).entrySet()) {
            ApplicationRuntimeResolver.VerifiedProcess verified = byPid.get(entry.getKey());
            if (verified == null) return identityUnavailable();
            WindowService.CloseResult result = windowService.close(entry.getValue(),
                    () -> valid(target.identityApplication(), verified));
            if (result == WindowService.CloseResult.IDENTITY_CHANGED) return identityUnavailable();
            if (result == WindowService.CloseResult.REJECTED) {
                return ApplicationActionResult.failed("Windows rejected WM_CLOSE");
            }
        }
        waitForWindowsClosed(target.windows(), closeTimeout);
        return target.windows().stream().anyMatch(windowService::exists)
                ? ApplicationActionResult.failed("Application windows remained open")
                : ApplicationActionResult.success();
    }

    @Override
    public ApplicationActionResult focus(ApplicationDefinition applicationDefinition) {
        TargetResolution target = resolveTarget(applicationDefinition);
        ApplicationActionResult terminal = terminalResult(target);
        if (terminal != null) return terminal;
        if (target.windows().isEmpty()) {
            return ApplicationActionResult.of(ApplicationActionResult.Status.NO_VISIBLE_WINDOW);
        }
        WindowService.WindowHandle window = target.windows().getFirst();
        ApplicationRuntimeResolver.VerifiedProcess verified = target.processes().stream()
                .filter(process -> process.pid() == window.processId()).findFirst().orElse(null);
        if (verified == null) return identityUnavailable();
        WindowService.FocusResult result = windowService.focus(window,
                () -> valid(target.identityApplication(), verified));
        return switch (result) {
            case FOCUSED -> ApplicationActionResult.success();
            case REJECTED -> ApplicationActionResult.of(ApplicationActionResult.Status.FOCUS_REJECTED);
            case IDENTITY_CHANGED -> identityUnavailable();
        };
    }

    @Override
    public ApplicationActionResult runtimeState(ApplicationDefinition applicationDefinition) {
        TargetResolution target = resolveTarget(applicationDefinition);
        if (target.status() == TargetStatus.IDENTITY_UNAVAILABLE) return identityUnavailable();
        if (target.status() == TargetStatus.NOT_RUNNING) {
            return ApplicationActionResult.status(ApplicationRuntimeState.NOT_RUNNING);
        }
        if (target.windowScoped() && target.windows().isEmpty()) {
            return ApplicationActionResult.status(ApplicationRuntimeState.NOT_RUNNING);
        }
        return ApplicationActionResult.status(target.windows().isEmpty()
                ? ApplicationRuntimeState.RUNNING_BACKGROUND : ApplicationRuntimeState.RUNNING_WITH_WINDOW);
    }

    @Override
    public OpenApplicationsResult runningApplications() {
        ApplicationRuntimeResolver.CatalogResolution catalogResolution = runtimeResolver.resolveAll();
        if (catalogResolution.status() != ApplicationRuntimeResolver.CatalogResolution.Status.COMPLETE) {
            return OpenApplicationsResult.failed(catalogResolution.detail());
        }
        Set<Long> pids = catalogResolution.resolutions().values().stream()
                .filter(resolution -> resolution.status() == ApplicationRuntimeResolver.Resolution.Status.RESOLVED)
                .flatMap(resolution -> resolution.processes().stream())
                .map(ApplicationRuntimeResolver.VerifiedProcess::pid).collect(Collectors.toSet());
        List<WindowService.WindowHandle> windows = windowService.visibleWindows(pids);

        List<OpenApplicationsResult.Entry> open = new ArrayList<>();
        int unverifiable = 0;
        for (ApplicationDefinition application : catalogResolution.catalog()) {
            TargetResolution target = resolveFromBatch(application, catalogResolution, windows);
            if (target.status() == TargetStatus.RESOLVED && !target.windows().isEmpty()) {
                open.add(new OpenApplicationsResult.Entry(application, ApplicationRuntimeState.RUNNING_WITH_WINDOW));
            }
            else if (target.status() == TargetStatus.IDENTITY_UNAVAILABLE && target.candidateObserved()) {
                unverifiable++;
            }
        }
        open.sort(Comparator.comparing(entry -> entry.application().getDisplayName(), String.CASE_INSENSITIVE_ORDER));
        return OpenApplicationsResult.success(open, unverifiable);
    }

    private TargetResolution resolveTarget(ApplicationDefinition application) {
        List<ApplicationDefinition> catalog = definitions.get();
        ApplicationRuntimeResolver.Resolution direct = runtimeResolver.resolve(application);
        if (direct.status() == ApplicationRuntimeResolver.Resolution.Status.RESOLVED) {
            List<WindowService.WindowHandle> windows = windowsFor(direct.processes());
            return directTarget(application, catalog, direct, windows);
        }
        TargetResolution hosted = hostedTarget(application, catalog, direct, null, null);
        if (hosted != null) return hosted;
        return fromTerminal(direct);
    }

    private TargetResolution resolveFromBatch(ApplicationDefinition application,
                                              ApplicationRuntimeResolver.CatalogResolution batch,
                                              List<WindowService.WindowHandle> windows) {
        ApplicationRuntimeResolver.Resolution direct = batch.resolutions()
                .get(ApplicationCatalogIdentity.stableKey(application));
        if (direct == null) return TargetResolution.unavailable(false);
        if (direct.status() == ApplicationRuntimeResolver.Resolution.Status.RESOLVED) {
            return directTarget(application, batch.catalog(), direct, windowsFor(direct.processes(), windows));
        }
        TargetResolution hosted = hostedTarget(application, batch.catalog(), direct, batch.resolutions(), windows);
        if (hosted != null) return hosted;
        return fromTerminal(direct);
    }

    private TargetResolution directTarget(ApplicationDefinition application, List<ApplicationDefinition> catalog,
                                          ApplicationRuntimeResolver.Resolution direct,
                                          List<WindowService.WindowHandle> windows) {
        if (!hasHostedChildren(application, catalog)) {
            return TargetResolution.resolved(application, direct.processes(), windows, false,
                    direct.candidateObserved());
        }
        ApplicationProcessIdentity identity = application.getProcessIdentity();
        if (!canAssociateWindows(identity)) return TargetResolution.unavailable(!windows.isEmpty());
        List<WindowService.WindowHandle> owned = matchingWindows(identity, windows);
        return TargetResolution.resolved(application, direct.processes(), owned, true,
                direct.candidateObserved() || !owned.isEmpty());
    }

    private TargetResolution hostedTarget(ApplicationDefinition application, List<ApplicationDefinition> catalog,
                                          ApplicationRuntimeResolver.Resolution direct,
                                          Map<String, ApplicationRuntimeResolver.Resolution> batch,
                                          List<WindowService.WindowHandle> allWindows) {
        ApplicationProcessIdentity identity = application.getProcessIdentity();
        if (identity.hostApplicationId().isBlank()) return null;
        ApplicationDefinition host = find(catalog, identity.hostApplicationId());
        if (host == null || !canAssociateWindows(identity)) {
            return TargetResolution.unavailable(direct.candidateObserved());
        }
        ApplicationRuntimeResolver.Resolution hostResolution = batch == null
                ? runtimeResolver.resolve(host) : batch.get(ApplicationCatalogIdentity.stableKey(host));
        if (hostResolution == null
                || hostResolution.status() == ApplicationRuntimeResolver.Resolution.Status.IDENTITY_UNAVAILABLE) {
            return TargetResolution.unavailable(direct.candidateObserved()
                    || (hostResolution != null && hostResolution.candidateObserved()));
        }
        if (hostResolution.status() == ApplicationRuntimeResolver.Resolution.Status.NOT_RUNNING) {
            return TargetResolution.notRunning();
        }
        List<WindowService.WindowHandle> hostWindows = allWindows == null
                ? windowsFor(hostResolution.processes()) : windowsFor(hostResolution.processes(), allWindows);
        List<WindowService.WindowHandle> owned = matchingWindows(identity, hostWindows);
        if (owned.isEmpty()) return TargetResolution.notRunning();
        return TargetResolution.resolved(host, hostResolution.processes(), owned, true, true);
    }

    private boolean canAssociateWindows(ApplicationProcessIdentity identity) {
        return identity.windowAssociationEnabled() && !identity.windowSignatures().isEmpty();
    }

    private List<WindowService.WindowHandle> matchingWindows(ApplicationProcessIdentity identity,
                                                             List<WindowService.WindowHandle> windows) {
        return windows.stream().filter(window -> identity.windowSignatures().stream()
                .anyMatch(signature -> signature.matches(window))).toList();
    }

    private boolean hasHostedChildren(ApplicationDefinition application, List<ApplicationDefinition> catalog) {
        String id = ApplicationCatalogIdentity.canonicalId(application.getId());
        return id != null && catalog.stream().anyMatch(other -> id.equals(
                ApplicationCatalogIdentity.canonicalId(
                        other.getProcessIdentity().hostApplicationId())));
    }

    private List<WindowService.WindowHandle> windowsFor(
            List<ApplicationRuntimeResolver.VerifiedProcess> processes) {
        Set<Long> pids = processes.stream().map(ApplicationRuntimeResolver.VerifiedProcess::pid)
                .collect(Collectors.toSet());
        return windowService.visibleWindows(pids);
    }

    private List<WindowService.WindowHandle> windowsFor(
            List<ApplicationRuntimeResolver.VerifiedProcess> processes,
            List<WindowService.WindowHandle> windows) {
        Set<Long> pids = processes.stream().map(ApplicationRuntimeResolver.VerifiedProcess::pid)
                .collect(Collectors.toSet());
        return windows.stream().filter(window -> pids.contains(window.processId())).toList();
    }

    private TargetResolution fromTerminal(ApplicationRuntimeResolver.Resolution resolution) {
        return resolution.status() == ApplicationRuntimeResolver.Resolution.Status.NOT_RUNNING
                ? TargetResolution.notRunning() : TargetResolution.unavailable(resolution.candidateObserved());
    }

    private ApplicationActionResult terminalResult(TargetResolution target) {
        return switch (target.status()) {
            case IDENTITY_UNAVAILABLE -> identityUnavailable();
            case NOT_RUNNING -> ApplicationActionResult.of(ApplicationActionResult.Status.NOT_RUNNING);
            case RESOLVED -> null;
        };
    }

    private boolean valid(ApplicationDefinition identityApplication,
                          ApplicationRuntimeResolver.VerifiedProcess verified) {
        return runtimeResolver.revalidate(identityApplication, verified).status()
                == ApplicationRuntimeResolver.Revalidation.Status.VALID;
    }

    private ApplicationDefinition find(List<ApplicationDefinition> catalog, String id) {
        String canonicalId = ApplicationCatalogIdentity.canonicalId(id);
        if (canonicalId == null) return null;
        return catalog.stream().filter(application -> canonicalId.equals(
                ApplicationCatalogIdentity.canonicalId(application.getId()))).findFirst().orElse(null);
    }

    private ApplicationActionResult identityUnavailable() {
        return ApplicationActionResult.of(ApplicationActionResult.Status.PROCESS_IDENTITY_UNAVAILABLE);
    }

    private List<ProcessHandle> availableHandles(List<ApplicationRuntimeResolver.VerifiedProcess> processes) {
        List<ProcessHandle> handles = new ArrayList<>();
        for (ApplicationRuntimeResolver.VerifiedProcess process : processes) {
            processHandle(process.pid()).ifPresent(handles::add);
        }
        return handles;
    }

    private Optional<ProcessHandle> processHandle(long pid) {
        try {
            return processHandles.apply(pid);
        }
        catch (RuntimeException error) {
            return Optional.empty();
        }
    }

    private ApplicationActionResult terminationResult(ApplicationDefinition application,
                                                      List<ApplicationRuntimeResolver.VerifiedProcess> processes) {
        boolean stillRunning = false;
        for (ApplicationRuntimeResolver.VerifiedProcess process : processes) {
            ApplicationRuntimeResolver.Revalidation observation = runtimeResolver.revalidate(application, process);
            if (observation.status() == ApplicationRuntimeResolver.Revalidation.Status.UNVERIFIABLE) {
                return identityUnavailable();
            }
            if (observation.status() == ApplicationRuntimeResolver.Revalidation.Status.VALID) stillRunning = true;
        }
        return stillRunning ? ApplicationActionResult.failed("Application processes remained alive")
                : ApplicationActionResult.success();
    }

    private void waitForExit(List<ProcessHandle> handles, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (handles.stream().anyMatch(ProcessHandle::isAlive) && System.nanoTime() < deadline) sleepBriefly();
    }

    private void waitForWindowsClosed(List<WindowService.WindowHandle> windows, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (windows.stream().anyMatch(windowService::exists) && System.nanoTime() < deadline) sleepBriefly();
    }

    private void sleepBriefly() {
        try {
            Thread.sleep(25);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private enum TargetStatus { RESOLVED, NOT_RUNNING, IDENTITY_UNAVAILABLE }

    private record TargetResolution(TargetStatus status, ApplicationDefinition identityApplication,
                                    List<ApplicationRuntimeResolver.VerifiedProcess> processes,
                                    List<WindowService.WindowHandle> windows, boolean windowScoped,
                                    boolean candidateObserved) {
        static TargetResolution resolved(ApplicationDefinition identityApplication,
                                         List<ApplicationRuntimeResolver.VerifiedProcess> processes,
                                         List<WindowService.WindowHandle> windows, boolean windowScoped,
                                         boolean candidateObserved) {
            return new TargetResolution(TargetStatus.RESOLVED, identityApplication, List.copyOf(processes),
                    List.copyOf(windows), windowScoped, candidateObserved);
        }

        static TargetResolution notRunning() {
            return new TargetResolution(TargetStatus.NOT_RUNNING, null, List.of(), List.of(), false, false);
        }

        static TargetResolution unavailable(boolean candidateObserved) {
            return new TargetResolution(TargetStatus.IDENTITY_UNAVAILABLE, null, List.of(), List.of(),
                    false, candidateObserved);
        }
    }
}

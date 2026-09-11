package com.fuad.assistant.skills.os;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class WindowsApplicationController implements ApplicationController {
    private static final Duration CLOSE_TIMEOUT = Duration.ofSeconds(2);
    private final WindowService windowService;
    private final Supplier<List<ProcessHandle>> processes;

    public WindowsApplicationController() {
        this(new JnaWindowService(), () -> ProcessHandle.allProcesses().toList());
    }

    WindowsApplicationController(WindowService windowService, Supplier<List<ProcessHandle>> processes) {
        this.windowService = windowService;
        this.processes = processes;
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
        if (applicationDefinition.getProcessIdentity().isEmpty()) {
            return ApplicationActionResult.of(ApplicationActionResult.Status.PROCESS_IDENTITY_UNAVAILABLE);
        }
        List<ProcessHandle> matches = matchingProcesses(applicationDefinition);
        if (matches.isEmpty()) return ApplicationActionResult.of(ApplicationActionResult.Status.NOT_RUNNING);
        Set<Long> pids = matches.stream().map(ProcessHandle::pid).collect(Collectors.toSet());
        List<WindowService.WindowHandle> windows = windowService.visibleWindows(pids);
        windows.forEach(windowService::close);
        if (!windows.isEmpty()) waitForExit(matches, CLOSE_TIMEOUT);
        List<ProcessHandle> remaining = matches.stream().filter(ProcessHandle::isAlive).toList();
        remaining.forEach(ProcessHandle::destroy);
        waitForExit(remaining, CLOSE_TIMEOUT);
        return remaining.stream().noneMatch(ProcessHandle::isAlive)
                ? ApplicationActionResult.success()
                : ApplicationActionResult.failed("Application processes remained alive");
    }

    @Override
    public ApplicationActionResult focus(ApplicationDefinition applicationDefinition) {
        if (applicationDefinition.getProcessIdentity().isEmpty()) {
            return ApplicationActionResult.of(ApplicationActionResult.Status.PROCESS_IDENTITY_UNAVAILABLE);
        }
        List<ProcessHandle> matches = matchingProcesses(applicationDefinition);
        if (matches.isEmpty()) return ApplicationActionResult.of(ApplicationActionResult.Status.NOT_RUNNING);
        Set<Long> pids = matches.stream().map(ProcessHandle::pid).collect(Collectors.toSet());
        List<WindowService.WindowHandle> windows = windowService.visibleWindows(pids);
        if (windows.isEmpty()) return ApplicationActionResult.of(ApplicationActionResult.Status.NO_VISIBLE_WINDOW);
        return windowService.focus(windows.getFirst()) == WindowService.FocusResult.FOCUSED
                ? ApplicationActionResult.success()
                : ApplicationActionResult.of(ApplicationActionResult.Status.FOCUS_REJECTED);
    }

    @Override
    public ApplicationActionResult runtimeState(ApplicationDefinition applicationDefinition) {
        if (applicationDefinition.getProcessIdentity().isEmpty()) {
            return ApplicationActionResult.of(ApplicationActionResult.Status.PROCESS_IDENTITY_UNAVAILABLE);
        }
        List<ProcessHandle> matches = matchingProcesses(applicationDefinition);
        if (matches.isEmpty()) return ApplicationActionResult.status(ApplicationRuntimeState.NOT_RUNNING);
        Set<Long> pids = matches.stream().map(ProcessHandle::pid).collect(Collectors.toSet());
        return ApplicationActionResult.status(windowService.visibleWindows(pids).isEmpty()
                ? ApplicationRuntimeState.RUNNING_BACKGROUND
                : ApplicationRuntimeState.RUNNING_WITH_WINDOW);
    }

    private List<ProcessHandle> matchingProcesses(ApplicationDefinition definition) {
        return processes.get().stream().filter(ProcessHandle::isAlive)
                .filter(process -> matches(definition.getProcessIdentity(), process.info().command().orElse("")))
                .toList();
    }

    private boolean matches(ApplicationProcessIdentity identity, String command) {
        if (command.isBlank()) return false;
        String normalized = command.toLowerCase(Locale.ROOT);
        if (identity.executablePaths().stream().map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(normalized::equals)) return true;
        if (identity.packageRoots().stream().map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(root -> normalized.startsWith(root.endsWith("\\") ? root : root + "\\"))) return true;
        try {
            String filename = Path.of(command).getFileName().toString();
            return identity.processNames().stream().anyMatch(filename::equalsIgnoreCase);
        }
        catch (Exception e) {
            return false;
        }
    }

    private void waitForExit(List<ProcessHandle> handles, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (handles.stream().anyMatch(ProcessHandle::isAlive) && System.nanoTime() < deadline) {
            try {
                Thread.sleep(25);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}

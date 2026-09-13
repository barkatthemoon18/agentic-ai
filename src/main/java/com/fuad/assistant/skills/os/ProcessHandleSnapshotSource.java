package com.fuad.assistant.skills.os;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/** Test and compatibility source whose observations come entirely from ProcessHandle. */
final class ProcessHandleSnapshotSource implements WindowsProcessSnapshotSource {
    private final Supplier<List<ProcessHandle>> processes;

    ProcessHandleSnapshotSource(Supplier<List<ProcessHandle>> processes) {
        this.processes = processes;
    }

    @Override
    public ProcessSnapshotBatch snapshotAll() {
        try {
            List<WindowsProcessSnapshot> snapshots = processes.get().stream()
                    .filter(ProcessHandle::isAlive).map(this::snapshotOf).toList();
            return ProcessSnapshotBatch.complete(snapshots);
        }
        catch (RuntimeException error) {
            return ProcessSnapshotBatch.failed(ProcessObservationFailure.UNKNOWN, error.getMessage());
        }
    }

    @Override
    public ProcessSnapshotLookup snapshot(long pid) {
        try {
            return processes.get().stream().filter(process -> process.pid() == pid && process.isAlive())
                    .findFirst().map(process -> ProcessSnapshotLookup.found(snapshotOf(process)))
                    .orElseGet(ProcessSnapshotLookup::notFound);
        }
        catch (RuntimeException error) {
            return ProcessSnapshotLookup.failed(ProcessObservationFailure.UNKNOWN, error.getMessage());
        }
    }

    private WindowsProcessSnapshot snapshotOf(ProcessHandle process) {
        ProcessHandle.Info info = process.info();
        String command = info.command().orElse("").trim();
        List<String> lineTokens = info.commandLine().map(WindowsCommandLineTokenizer::tokenize)
                .map(ArrayList::new).orElseGet(ArrayList::new);
        if (command.isBlank() && !lineTokens.isEmpty()) command = lineTokens.getFirst();
        Optional<String[]> exposedArguments = info.arguments();
        List<String> arguments;
        boolean argumentsAvailable;
        if (exposedArguments.isPresent()) {
            arguments = List.copyOf(Arrays.asList(exposedArguments.get()));
            argumentsAvailable = true;
        }
        else if (info.commandLine().isPresent()) {
            if (!lineTokens.isEmpty()) lineTokens.removeFirst();
            arguments = List.copyOf(lineTokens);
            argumentsAvailable = true;
        }
        else {
            arguments = List.of();
            argumentsAvailable = false;
        }
        Optional<Path> path;
        try {
            path = isAbsoluteWindowsPath(command) ? Optional.of(Path.of(command)) : Optional.empty();
        }
        catch (RuntimeException ignored) {
            path = Optional.empty();
        }
        String name = path.map(Path::getFileName).map(Path::toString).orElse(command);
        long parentPid = process.parent().map(ProcessHandle::pid).orElse(0L);
        return new WindowsProcessSnapshot(process.pid(), parentPid, info.startInstant(), path, name,
                arguments, argumentsAvailable);
    }

    private boolean isAbsoluteWindowsPath(String value) {
        return value != null && (value.matches("^[A-Za-z]:[\\\\/].*") || value.startsWith("\\\\"));
    }
}

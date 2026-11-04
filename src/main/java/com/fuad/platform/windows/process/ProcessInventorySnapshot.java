package com.fuad.platform.windows.process;

import java.util.List;
import java.util.Objects;

public record ProcessInventorySnapshot(
        List<ProcessEntry> processes,
        boolean complete) {

    public ProcessInventorySnapshot {
        processes = List.copyOf(Objects.requireNonNull(processes));
    }

    public static ProcessInventorySnapshot completed(List<ProcessEntry> processes) {
        return new ProcessInventorySnapshot(processes, true);
    }

    public static ProcessInventorySnapshot incomplete(List<ProcessEntry> processes) {
        return new ProcessInventorySnapshot(processes, false);
    }

    public boolean containsExecutable(String executableName) {
        String expected;

        expected = Objects.requireNonNull(executableName).trim();
        if (expected.isEmpty()) {
            throw new IllegalArgumentException("Executable name cannot be blank");
        }
        return processes.stream().anyMatch(process -> process.executableName().equalsIgnoreCase(expected));
    }

    public record ProcessEntry(
            long pid,
            String executableName) {

        public ProcessEntry {
            if (pid < 0) {
                throw new IllegalArgumentException("PID cannot be negative");
            }
            executableName = Objects.requireNonNull(executableName).trim();
            if (executableName.isEmpty()) {
                throw new IllegalArgumentException("Executable name cannot be blank");
            }
        }
    }
}

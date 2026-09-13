package com.fuad.assistant.skills.os;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

record WindowsProcessSnapshot(long pid, long parentProcessId, Optional<Instant> creationTime, Optional<Path> executablePath,
                              String executableName, List<String> arguments, boolean argumentsAvailable) {
    WindowsProcessSnapshot(long pid, Optional<Instant> creationTime, Optional<Path> executablePath,
                           String executableName, List<String> arguments, boolean argumentsAvailable) {
        this(pid, 0, creationTime, executablePath, executableName, arguments, argumentsAvailable);
    }

    WindowsProcessSnapshot {
        if (pid <= 0) throw new IllegalArgumentException("pid must be positive");
        if (parentProcessId < 0) throw new IllegalArgumentException("parentProcessId cannot be negative");
        creationTime = creationTime == null ? Optional.empty() : creationTime;
        executablePath = executablePath == null ? Optional.empty() : executablePath;
        executableName = executableName == null ? "" : executableName.trim();
        arguments = arguments == null ? List.of() : List.copyOf(arguments);
    }
}

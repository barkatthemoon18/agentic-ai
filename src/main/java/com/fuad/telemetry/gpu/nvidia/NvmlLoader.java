package com.fuad.telemetry.gpu.nvidia;

import com.sun.jna.Native;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class NvmlLoader {
    private NvmlLoader() {
        /* Empty intentionally */
    }

    static NvmlLibrary load() {
        List<Path> candidates = new ArrayList<>();
        String programW6432;
        String systemRoot;

        systemRoot = System.getenv("SystemRoot");
        if (systemRoot != null) {
            candidates.add(Path.of(systemRoot, "System32", "nvml.dll"));
        }
        programW6432 = System.getProperty("ProgramW6432");
        if (programW6432 != null) {
            candidates.add(Path.of(programW6432, "Nvidia Corporation", "NVSMI", "nvml.dll"));
        }
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return Native.load(candidate.toString(), NvmlLibrary.class);
            }
        }
        return Native.load("nvml", NvmlLibrary.class);
    }
}

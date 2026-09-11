package com.fuad.assistant.skills.os;

import java.util.HashSet;
import java.util.Set;

public record ApplicationProcessIdentity(Set<String> executablePaths,
                                         Set<String> packageRoots,
                                         Set<String> processNames) {
    public ApplicationProcessIdentity {
        executablePaths = Set.copyOf(executablePaths);
        packageRoots = Set.copyOf(packageRoots);
        processNames = Set.copyOf(processNames);
    }

    public static ApplicationProcessIdentity empty() {
        return new ApplicationProcessIdentity(Set.of(), Set.of(), Set.of());
    }

    public boolean isEmpty() {
        return executablePaths.isEmpty() && packageRoots.isEmpty() && processNames.isEmpty();
    }

    public ApplicationProcessIdentity withProcessNames(Set<String> additionalNames) {
        Set<String> merged = new HashSet<>(processNames);
        merged.addAll(additionalNames);
        return new ApplicationProcessIdentity(executablePaths, packageRoots, merged);
    }
}

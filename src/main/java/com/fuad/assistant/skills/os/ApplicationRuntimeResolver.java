package com.fuad.assistant.skills.os;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

final class ApplicationRuntimeResolver {
    private final Supplier<List<ApplicationDefinition>> definitions;
    private final WindowsProcessSnapshotSource snapshots;

    ApplicationRuntimeResolver(Supplier<List<ApplicationDefinition>> definitions,
                               WindowsProcessSnapshotSource snapshots) {
        this.definitions = definitions;
        this.snapshots = snapshots;
    }

    Resolution resolve(ApplicationDefinition requested) {
        List<ApplicationDefinition> catalog = definitions.get();
        ProcessSnapshotBatch observation = snapshots.snapshotAll();
        if (observation.status() != ProcessSnapshotBatch.Status.COMPLETE) return Resolution.unavailable(true);
        return resolveAgainst(requested, catalog, observation.snapshots());
    }

    CatalogResolution resolveAll() {
        List<ApplicationDefinition> catalog = definitions.get();
        ProcessSnapshotBatch observation = snapshots.snapshotAll();
        if (observation.status() != ProcessSnapshotBatch.Status.COMPLETE) {
            return CatalogResolution.failed(observation.detail());
        }
        Map<String, Resolution> resolutions = new LinkedHashMap<>();
        for (ApplicationDefinition application : catalog) {
            resolutions.put(application.getId(), resolveAgainst(application, catalog, observation.snapshots()));
        }
        return CatalogResolution.complete(catalog, resolutions);
    }

    Revalidation revalidate(ApplicationDefinition requested, VerifiedProcess verified) {
        ProcessSnapshotLookup observation = snapshots.snapshot(verified.pid());
        if (observation.status() == ProcessSnapshotLookup.Status.NOT_FOUND) return Revalidation.gone();
        if (observation.status() != ProcessSnapshotLookup.Status.FOUND) return Revalidation.unverifiable();
        WindowsProcessSnapshot current = observation.snapshot();
        if (current.creationTime().isEmpty()) return Revalidation.unverifiable();
        if (!verified.creationTime().equals(current.creationTime().get())) return Revalidation.unverifiable();
        if (!sameObservedIdentity(verified.snapshot(), current)) return Revalidation.unverifiable();

        List<ApplicationDefinition> catalog = definitions.get();
        ApplicationDefinition target = find(catalog, requested.getId());
        if (target == null || !hasStrongIdentity(target, catalog)) return Revalidation.unverifiable();
        return classify(target, current, catalog) == CandidateResult.MATCH
                ? Revalidation.valid() : Revalidation.unverifiable();
    }

    private Resolution resolveAgainst(ApplicationDefinition requested, List<ApplicationDefinition> catalog,
                                      List<WindowsProcessSnapshot> processes) {
        ApplicationDefinition target = find(catalog, requested.getId());
        if (target == null) return Resolution.unavailable(false);
        boolean candidateObserved = processes.stream().anyMatch(process -> isCandidate(target, process));
        if (!hasStrongIdentity(target, catalog)) return Resolution.unavailable(candidateObserved);

        List<VerifiedProcess> matches = new ArrayList<>();
        boolean unverifiable = false;
        for (WindowsProcessSnapshot process : processes) {
            if (!isCandidate(target, process)) continue;
            CandidateResult result = classify(target, process, catalog);
            if (result == CandidateResult.MATCH) matches.add(VerifiedProcess.from(process));
            else if (result == CandidateResult.UNVERIFIABLE) unverifiable = true;
        }
        if (unverifiable) return Resolution.unavailable(true);
        return matches.isEmpty() ? Resolution.notRunning() : Resolution.resolved(matches);
    }

    private CandidateResult classify(ApplicationDefinition target, WindowsProcessSnapshot process,
                                     List<ApplicationDefinition> catalog) {
        if (!isCandidate(target, process)) return CandidateResult.NO_MATCH;
        if (process.creationTime().isEmpty()) return CandidateResult.UNVERIFIABLE;
        ApplicationProcessIdentity identity = target.getProcessIdentity();
        boolean hasExpectedPath = !identity.executablePaths().isEmpty() || !identity.packageRoots().isEmpty();
        String command = process.executablePath().map(Path::toString).orElse("");
        boolean expectedPathMatches = matchesExpectedPath(identity, command);

        if (process.executablePath().isPresent()) {
            if (hasExpectedPath && !expectedPathMatches) return CandidateResult.NO_MATCH;
            if (!hasExpectedPath) return CandidateResult.UNVERIFIABLE;
            if (matchesExclusivePath(target, command, catalog)) return CandidateResult.MATCH;
        }
        else if (matchesExclusiveTrustedName(target, process.executableName(), catalog)) {
            return CandidateResult.MATCH;
        }

        boolean hasArgumentSignature = !identity.commandLineArgumentSets().isEmpty()
                || !identity.exactCommandLineArgumentSets().isEmpty();
        if (!hasArgumentSignature || !process.argumentsAvailable()) return CandidateResult.UNVERIFIABLE;
        if (!matchesArgumentSignature(identity, process.arguments())) return CandidateResult.NO_MATCH;

        Set<String> compatibleIds = new HashSet<>();
        for (ApplicationDefinition definition : catalog) {
            if (!isCandidate(definition, process)) continue;
            ApplicationProcessIdentity candidate = definition.getProcessIdentity();
            if (process.executablePath().isPresent()
                    && (!candidate.executablePaths().isEmpty() || !candidate.packageRoots().isEmpty())
                    && !matchesExpectedPath(candidate, command)) continue;
            if (matchesArgumentSignature(candidate, process.arguments())) compatibleIds.add(definition.getId());
        }
        return compatibleIds.size() == 1 && compatibleIds.contains(target.getId())
                && hasExclusiveMatchingSignature(target, process.arguments(), catalog)
                ? CandidateResult.MATCH : CandidateResult.UNVERIFIABLE;
    }

    private boolean hasStrongIdentity(ApplicationDefinition target, List<ApplicationDefinition> catalog) {
        ApplicationProcessIdentity identity = target.getProcessIdentity();
        if (identity.executablePaths().stream().anyMatch(path -> exactPathExclusive(target, path, catalog))) return true;
        if (identity.packageRoots().stream().anyMatch(root -> packageRootExclusive(target, root, catalog))) return true;
        if (identity.trustedProcessNamesWhenPathUnavailable().stream()
                .anyMatch(name -> trustedNameExclusive(target, name, catalog))) return true;
        boolean hasLocator = !candidateNames(target).isEmpty() || !identity.packageRoots().isEmpty();
        return hasLocator && (identity.commandLineArgumentSets().stream()
                .anyMatch(arguments -> containsSignatureExclusive(target, arguments, catalog))
                || identity.exactCommandLineArgumentSets().stream()
                .anyMatch(arguments -> exactSignatureExclusive(target, arguments, catalog)));
    }

    private boolean hasExclusiveMatchingSignature(ApplicationDefinition target, List<String> observed,
                                                   List<ApplicationDefinition> catalog) {
        ApplicationProcessIdentity identity = target.getProcessIdentity();
        return identity.exactCommandLineArgumentSets().stream()
                .anyMatch(arguments -> arguments.equals(observed)
                        && exactSignatureExclusive(target, arguments, catalog))
                || identity.commandLineArgumentSets().stream()
                .anyMatch(arguments -> observed.containsAll(arguments)
                        && containsSignatureExclusive(target, arguments, catalog));
    }

    private boolean matchesArgumentSignature(ApplicationProcessIdentity identity, List<String> arguments) {
        return identity.exactCommandLineArgumentSets().stream().anyMatch(arguments::equals)
                || identity.commandLineArgumentSets().stream().anyMatch(arguments::containsAll);
    }

    private boolean exactSignatureExclusive(ApplicationDefinition target, List<String> arguments,
                                            List<ApplicationDefinition> catalog) {
        return catalog.stream().filter(other -> !other.getId().equals(target.getId()))
                .filter(other -> sharesHost(target, other))
                .noneMatch(other -> other.getProcessIdentity().exactCommandLineArgumentSets().contains(arguments)
                        || other.getProcessIdentity().commandLineArgumentSets().stream()
                        .anyMatch(required -> arguments.containsAll(required)));
    }

    private boolean containsSignatureExclusive(ApplicationDefinition target, Set<String> arguments,
                                               List<ApplicationDefinition> catalog) {
        if (arguments.isEmpty()) return false;
        return catalog.stream().filter(other -> !other.getId().equals(target.getId()))
                .filter(other -> sharesHost(target, other))
                .noneMatch(other -> other.getProcessIdentity().commandLineArgumentSets().stream()
                        .anyMatch(candidate -> arguments.containsAll(candidate) || candidate.containsAll(arguments))
                        || other.getProcessIdentity().exactCommandLineArgumentSets().stream()
                        .anyMatch(candidate -> candidate.containsAll(arguments)));
    }

    private boolean matchesExclusivePath(ApplicationDefinition target, String command,
                                         List<ApplicationDefinition> catalog) {
        ApplicationProcessIdentity identity = target.getProcessIdentity();
        return identity.executablePaths().stream()
                .anyMatch(path -> samePath(path, command) && exactPathExclusive(target, path, catalog))
                || identity.packageRoots().stream()
                .anyMatch(root -> withinRoot(command, root) && packageRootExclusive(target, root, catalog));
    }

    private boolean exactPathExclusive(ApplicationDefinition target, String path,
                                       List<ApplicationDefinition> catalog) {
        String normalized = normalizePath(path);
        return catalog.stream().filter(other -> !other.getId().equals(target.getId()))
                .noneMatch(other -> other.getProcessIdentity().executablePaths().stream()
                                .anyMatch(candidate -> normalizePath(candidate).equals(normalized))
                        || other.getProcessIdentity().packageRoots().stream()
                                .anyMatch(root -> withinRoot(normalized, root)));
    }

    private boolean packageRootExclusive(ApplicationDefinition target, String root,
                                         List<ApplicationDefinition> catalog) {
        String normalized = normalizeRoot(root);
        return catalog.stream().filter(other -> !other.getId().equals(target.getId()))
                .noneMatch(other -> other.getProcessIdentity().packageRoots().stream()
                                .map(ApplicationRuntimeResolver::normalizeRoot)
                                .anyMatch(candidate -> rootsOverlap(normalized, candidate))
                        || other.getProcessIdentity().executablePaths().stream()
                                .anyMatch(path -> withinRoot(path, normalized)));
    }

    private boolean trustedNameExclusive(ApplicationDefinition target, String name,
                                         List<ApplicationDefinition> catalog) {
        String normalized = normalizeName(name);
        return catalog.stream().filter(other -> !other.getId().equals(target.getId()))
                .noneMatch(other -> candidateNames(other).contains(normalized));
    }

    private boolean matchesExclusiveTrustedName(ApplicationDefinition target, String executableName,
                                                List<ApplicationDefinition> catalog) {
        return target.getProcessIdentity().trustedProcessNamesWhenPathUnavailable().stream()
                .anyMatch(name -> normalizeName(name).equals(normalizeName(executableName))
                        && trustedNameExclusive(target, name, catalog));
    }

    private boolean isCandidate(ApplicationDefinition definition, WindowsProcessSnapshot process) {
        ApplicationProcessIdentity identity = definition.getProcessIdentity();
        String command = process.executablePath().map(Path::toString).orElse("");
        if (!command.isBlank() && matchesExpectedPath(identity, command)) return true;
        return candidateNames(definition).contains(normalizeName(process.executableName()));
    }

    private boolean matchesExpectedPath(ApplicationProcessIdentity identity, String command) {
        return !command.isBlank() && (identity.executablePaths().stream().anyMatch(path -> samePath(path, command))
                || identity.packageRoots().stream().anyMatch(root -> withinRoot(command, root)));
    }

    private boolean sharesHost(ApplicationDefinition first, ApplicationDefinition second) {
        Set<String> names = candidateNames(first);
        return candidateNames(second).stream().anyMatch(names::contains)
                || first.getProcessIdentity().packageRoots().stream().anyMatch(firstRoot ->
                second.getProcessIdentity().packageRoots().stream()
                        .anyMatch(secondRoot -> rootsOverlap(normalizeRoot(firstRoot), normalizeRoot(secondRoot))));
    }

    private Set<String> candidateNames(ApplicationDefinition definition) {
        ApplicationProcessIdentity identity = definition.getProcessIdentity();
        Set<String> names = new HashSet<>();
        identity.processNames().stream().map(ApplicationRuntimeResolver::normalizeName).forEach(names::add);
        identity.trustedProcessNamesWhenPathUnavailable().stream()
                .map(ApplicationRuntimeResolver::normalizeName).forEach(names::add);
        identity.executablePaths().stream().map(ApplicationRuntimeResolver::fileName)
                .filter(name -> !name.isBlank()).forEach(names::add);
        names.remove("");
        return names;
    }

    private boolean sameObservedIdentity(WindowsProcessSnapshot first, WindowsProcessSnapshot second) {
        String firstPath = first.executablePath().map(Path::toString).orElse("");
        String secondPath = second.executablePath().map(Path::toString).orElse("");
        return samePath(firstPath, secondPath)
                && normalizeName(first.executableName()).equals(normalizeName(second.executableName()))
                && first.parentProcessId() == second.parentProcessId()
                && first.argumentsAvailable() == second.argumentsAvailable()
                && first.arguments().equals(second.arguments());
    }

    private ApplicationDefinition find(List<ApplicationDefinition> catalog, String id) {
        return catalog.stream().filter(application -> application.getId().equals(id)).findFirst().orElse(null);
    }

    private static boolean samePath(String first, String second) {
        return normalizePath(first).equals(normalizePath(second));
    }

    private static boolean withinRoot(String path, String root) {
        String normalizedPath = normalizePath(path);
        String normalizedRoot = normalizeRoot(root);
        return !normalizedRoot.isBlank() && (normalizedPath.equals(normalizedRoot)
                || normalizedPath.startsWith(normalizedRoot + "\\"));
    }

    private static boolean rootsOverlap(String first, String second) {
        return first.equals(second) || first.startsWith(second + "\\") || second.startsWith(first + "\\");
    }

    private static String normalizeRoot(String value) {
        String normalized = normalizePath(value);
        while (normalized.endsWith("\\") && normalized.length() > 3) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String normalizePath(String value) {
        if (value == null) return "";
        String normalized = value.trim();
        if (normalized.length() >= 2 && normalized.startsWith("\"") && normalized.endsWith("\"")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        try {
            normalized = Path.of(normalized).normalize().toString();
        }
        catch (RuntimeException ignored) { }
        return normalized.replace('/', '\\').toLowerCase(Locale.ROOT);
    }

    private static String fileName(String value) {
        String normalized = normalizePath(value);
        int separator = normalized.lastIndexOf('\\');
        return normalizeName(separator >= 0 ? normalized.substring(separator + 1) : normalized);
    }

    private static String normalizeName(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private enum CandidateResult { MATCH, NO_MATCH, UNVERIFIABLE }

    record VerifiedProcess(long pid, Instant creationTime, WindowsProcessSnapshot snapshot) {
        static VerifiedProcess from(WindowsProcessSnapshot snapshot) {
            return new VerifiedProcess(snapshot.pid(), snapshot.creationTime().orElseThrow(), snapshot);
        }
    }

    record Resolution(Status status, List<VerifiedProcess> processes, boolean candidateObserved) {
        enum Status { RESOLVED, NOT_RUNNING, IDENTITY_UNAVAILABLE }

        static Resolution resolved(List<VerifiedProcess> processes) {
            return new Resolution(Status.RESOLVED, List.copyOf(processes), true);
        }

        static Resolution notRunning() { return new Resolution(Status.NOT_RUNNING, List.of(), false); }
        static Resolution unavailable(boolean candidateObserved) {
            return new Resolution(Status.IDENTITY_UNAVAILABLE, List.of(), candidateObserved);
        }
    }

    record CatalogResolution(Status status, List<ApplicationDefinition> catalog,
                             Map<String, Resolution> resolutions, String detail) {
        enum Status { COMPLETE, OBSERVATION_FAILED }

        static CatalogResolution complete(List<ApplicationDefinition> catalog, Map<String, Resolution> resolutions) {
            return new CatalogResolution(Status.COMPLETE, List.copyOf(catalog), Map.copyOf(resolutions), null);
        }

        static CatalogResolution failed(String detail) {
            return new CatalogResolution(Status.OBSERVATION_FAILED, List.of(), Map.of(), detail);
        }
    }

    record Revalidation(Status status) {
        enum Status { VALID, GONE, UNVERIFIABLE }
        static Revalidation valid() { return new Revalidation(Status.VALID); }
        static Revalidation gone() { return new Revalidation(Status.GONE); }
        static Revalidation unverifiable() { return new Revalidation(Status.UNVERIFIABLE); }
    }
}

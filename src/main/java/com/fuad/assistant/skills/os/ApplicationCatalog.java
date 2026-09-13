package com.fuad.assistant.skills.os;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class ApplicationCatalog {
    private final ApplicationDiscovery discovery;
    private final ApplicationAliasConfigLoader configLoader;
    private final boolean enrichDefinitions;
    private final AtomicReference<State> state = new AtomicReference<>(State.unavailable("Not initialized"));

    public ApplicationCatalog(ApplicationDiscovery discovery, ApplicationAliasConfigLoader configLoader) {
        this(discovery, configLoader, true);
    }

    private ApplicationCatalog(ApplicationDiscovery discovery, ApplicationAliasConfigLoader configLoader,
                               boolean enrichDefinitions) {
        this.discovery = Objects.requireNonNull(discovery, "discovery cannot be null");
        this.configLoader = Objects.requireNonNull(configLoader, "configLoader cannot be null");
        this.enrichDefinitions = enrichDefinitions;
    }

    public static ApplicationCatalog fixed(Collection<ApplicationDefinition> definitions) {
        ApplicationCatalog catalog = new ApplicationCatalog(() -> List.copyOf(definitions),
                new ApplicationAliasConfigLoader(null), false);
        catalog.refresh();
        return catalog;
    }

    public boolean refresh() {
        try {
            List<ApplicationDefinition> discovered = discovery.discover();
            State next = buildState(discovered, configLoader.load());
            state.set(next);
            System.out.println("APPLICATION CATALOG -> " + next.applications.size() + " applications");
            return true;
        }
        catch (Exception e) {
            State current = state.get();
            if (!current.available) state.set(State.unavailable(e.getMessage()));
            System.err.println("APPLICATION CATALOG -> DEGRADED: " + e.getMessage());
            return false;
        }
    }

    public boolean isAvailable() {
        return state.get().available;
    }

    public String unavailableReason() {
        return state.get().error;
    }

    public ApplicationResolution resolve(String target, boolean refreshOnMiss) {
        ApplicationResolution result = resolveCurrent(target);
        if (refreshOnMiss && (result.status() == ApplicationResolution.Status.UNKNOWN
                || result.status() == ApplicationResolution.Status.CATALOG_UNAVAILABLE) && refresh()) {
            return resolveCurrent(target);
        }
        return result;
    }

    public List<ApplicationDefinition> search(String filter, boolean refreshIfUnavailable) {
        State current = state.get();
        if (!current.available && refreshIfUnavailable && refresh()) current = state.get();
        if (!current.available) return List.of();
        String normalized = ApplicationNames.normalize(filter);
        return current.applications.stream()
                .filter(app -> normalized.isBlank()
                        || ApplicationNames.normalize(app.getDisplayName()).contains(normalized)
                        || app.getAliases().stream().anyMatch(alias -> ApplicationNames.normalize(alias).contains(normalized)))
                .sorted(Comparator.comparing(ApplicationDefinition::getDisplayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public List<ApplicationDefinition> applications() {
        return state.get().available ? state.get().applications : List.of();
    }

    private ApplicationResolution resolveCurrent(String target) {
        State current = state.get();
        if (!current.available) return ApplicationResolution.unavailable();
        String normalized = ApplicationNames.normalize(target);
        if (normalized.isBlank()) return ApplicationResolution.unknown();
        ApplicationDefinition application = current.aliasIndex.get(normalized);
        if (application != null) return ApplicationResolution.found(application);
        List<ApplicationDefinition> ambiguous = current.ambiguousIndex.get(normalized);
        if (ambiguous != null) {
            return new ApplicationResolution(ApplicationResolution.Status.AMBIGUOUS, null, ambiguous);
        }
        if (current.removedAliases.contains(normalized)) return ApplicationResolution.unknown();
        return resolveNatural(current.applications, normalized);
    }

    private ApplicationResolution resolveNatural(List<ApplicationDefinition> applications, String normalizedTarget) {
        String compactTarget = ApplicationNames.compact(normalizedTarget);
        List<ApplicationDefinition> matches = applications.stream()
                .filter(app -> naturalNameMatch(app.getDisplayName(), normalizedTarget, compactTarget)
                        || app.getAliases().stream().anyMatch(alias ->
                        naturalNameMatch(alias, normalizedTarget, compactTarget)))
                .distinct()
                .sorted(Comparator.comparing(ApplicationDefinition::getDisplayName,
                        String.CASE_INSENSITIVE_ORDER))
                .toList();
        if (matches.size() == 1) return ApplicationResolution.found(matches.getFirst());
        if (matches.size() > 1) {
            return new ApplicationResolution(ApplicationResolution.Status.AMBIGUOUS, null, matches);
        }
        return ApplicationResolution.unknown();
    }

    private boolean naturalNameMatch(String candidate, String normalizedTarget, String compactTarget) {
        return (!compactTarget.isBlank() && ApplicationNames.compact(candidate).equals(compactTarget))
                || ApplicationNames.startsWithWholeTokens(candidate, normalizedTarget);
    }

    private State buildState(List<ApplicationDefinition> discovered, ApplicationAliasConfig config) {
        Map<String, ApplicationDefinition> byId = new LinkedHashMap<>();
        for (ApplicationDefinition app : discovered) {
            if (app.getId() == null || app.getId().isBlank() || app.getDisplayName() == null || app.getDisplayName().isBlank()) continue;
            byId.putIfAbsent(app.getId(), app);
        }

        Map<String, Set<String>> primaryCandidates = new HashMap<>();
        Map<String, Set<String>> derivedCandidates = new HashMap<>();
        for (ApplicationDefinition app : byId.values()) {
            addCandidate(primaryCandidates, app.getDisplayName(), app.getId());
            String withoutVersion = app.getDisplayName().replaceFirst("(?i)\\s+v?\\d+(?:[.\\-]\\d+)*.*$", "");
            if (!withoutVersion.equals(app.getDisplayName()) && withoutVersion.split("\\s+").length > 1) {
                addCandidate(derivedCandidates, withoutVersion, app.getId());
            }
            String[] words = ApplicationNames.normalize(app.getDisplayName()).split(" ");
            if (words.length > 1) {
                StringBuilder acronym = new StringBuilder();
                for (String word : words) if (word.matches("[a-z]+")) acronym.append(word.charAt(0));
                if (acronym.length() >= 2) addCandidate(derivedCandidates, acronym.toString(), app.getId());
            }
            for (String path : app.getProcessIdentity().executablePaths()) {
                try {
                    String filename = Path.of(path).getFileName().toString().replaceFirst("(?i)\\.exe$", "");
                    if (filename.length() >= 3) addCandidate(derivedCandidates, filename, app.getId());
                }
                catch (Exception ignored) { }
            }
        }

        Set<String> removed = new HashSet<>();
        config.removeAliases().forEach(value -> removed.add(ApplicationNames.normalize(value)));
        Map<String, ApplicationDefinition> index = new HashMap<>();
        Map<String, List<ApplicationDefinition>> ambiguous = new HashMap<>();
        Map<String, Set<String>> aliasesById = new HashMap<>();
        int collisions = registerAutomaticAliases(primaryCandidates, byId, removed, index, ambiguous, aliasesById,
                false);
        collisions += registerAutomaticAliases(derivedCandidates, byId, removed, index, ambiguous, aliasesById,
                true);
        if (collisions > 0) {
            System.out.println("APPLICATION ALIAS -> skipped " + collisions + " collisions");
        }

        for (Map.Entry<String, String> override : config.aliases().entrySet()) {
            ApplicationDefinition target = byId.get(override.getValue());
            if (target == null) {
                System.err.println("APPLICATION ALIAS -> unknown AppID: " + override.getValue());
                continue;
            }
            String alias = ApplicationNames.normalize(override.getKey());
            if (alias.isBlank()) continue;
            index.put(alias, target);
            ambiguous.remove(alias);
            aliasesById.computeIfAbsent(target.getId(), ignored -> new HashSet<>()).add(alias);
        }

        List<ApplicationDefinition> resolved = new ArrayList<>();
        Map<String, ApplicationDefinition> resolvedById = new HashMap<>();
        Map<String, String> inferredHosts = inferHostRelationships(byId.values());
        for (ApplicationDefinition app : byId.values()) {
            Set<String> processNames = new HashSet<>(config.processNames().getOrDefault(app.getId(), List.of()));
            Set<String> trustedProcessNames = new HashSet<>(config.trustedProcessNamesWhenPathUnavailable()
                    .getOrDefault(app.getId(), List.of()));
            List<Set<String>> commandLineArgumentSets = config.commandLineArgumentSets()
                    .getOrDefault(app.getId(), List.of()).stream()
                    .filter(arguments -> arguments != null && !arguments.isEmpty())
                    .map(Set::copyOf)
                    .toList();
            List<List<String>> exactCommandLineArgumentSets = config.exactCommandLineArgumentSets()
                    .getOrDefault(app.getId(), List.of()).stream()
                    .filter(Objects::nonNull).map(List::copyOf).toList();
            String hostApplicationId = config.hostRelationships()
                    .getOrDefault(app.getId(), inferredHosts.getOrDefault(app.getId(), ""));
            if (!hostApplicationId.isBlank()
                    && (!byId.containsKey(hostApplicationId) || hostApplicationId.equals(app.getId()))) {
                System.err.println("APPLICATION HOST -> invalid relationship for AppID: " + app.getId());
                hostApplicationId = "";
            }
            List<ApplicationWindowSignature> windowSignatures = config.windowSignatures()
                    .getOrDefault(app.getId(), List.of());
            boolean windowAssociationEnabled = Boolean.TRUE.equals(
                    config.windowAssociationsEnabled().get(app.getId()));
            ApplicationDefinition updated = enrichDefinitions
                    ? app.withCatalogConfiguration(aliasesById.getOrDefault(app.getId(), Set.of()), processNames,
                            trustedProcessNames, commandLineArgumentSets, exactCommandLineArgumentSets,
                            hostApplicationId, windowSignatures, windowAssociationEnabled)
                    : app;
            resolved.add(updated);
            resolvedById.put(updated.getId(), updated);
        }
        index.replaceAll((alias, old) -> resolvedById.get(old.getId()));
        ambiguous.replaceAll((alias, apps) -> apps.stream().map(app -> resolvedById.get(app.getId())).toList());
        return new State(true, null, List.copyOf(resolved), Map.copyOf(index), Map.copyOf(ambiguous),
                Set.copyOf(removed));
    }

    private Map<String, String> inferHostRelationships(Collection<ApplicationDefinition> applications) {
        Map<String, List<ApplicationDefinition>> byPath = new HashMap<>();
        for (ApplicationDefinition application : applications) {
            for (String path : application.getProcessIdentity().executablePaths()) {
                String normalized = path.replace('/', '\\').toLowerCase(Locale.ROOT);
                byPath.computeIfAbsent(normalized, ignored -> new ArrayList<>()).add(application);
            }
        }
        Map<String, String> inferred = new HashMap<>();
        for (List<ApplicationDefinition> group : byPath.values()) {
            List<ApplicationDefinition> bases = group.stream()
                    .filter(app -> app.getProcessIdentity().commandLineArgumentSets().isEmpty()).toList();
            List<ApplicationDefinition> hosted = group.stream()
                    .filter(app -> !app.getProcessIdentity().commandLineArgumentSets().isEmpty()).toList();
            if (bases.size() != 1 || hosted.isEmpty() || !hostedSignaturesAreExclusive(hosted)) continue;
            hosted.forEach(app -> inferred.putIfAbsent(app.getId(), bases.getFirst().getId()));
        }
        return Map.copyOf(inferred);
    }

    private boolean hostedSignaturesAreExclusive(List<ApplicationDefinition> hosted) {
        for (int first = 0; first < hosted.size(); first++) {
            for (int second = first + 1; second < hosted.size(); second++) {
                for (Set<String> left : hosted.get(first).getProcessIdentity().commandLineArgumentSets()) {
                    for (Set<String> right : hosted.get(second).getProcessIdentity().commandLineArgumentSets()) {
                        if (left.containsAll(right) || right.containsAll(left)) return false;
                    }
                }
            }
        }
        return true;
    }

    private static int registerAutomaticAliases(Map<String, Set<String>> candidates,
                                                Map<String, ApplicationDefinition> byId,
                                                Set<String> removed,
                                                Map<String, ApplicationDefinition> index,
                                                Map<String, List<ApplicationDefinition>> ambiguous,
                                                Map<String, Set<String>> aliasesById,
                                                boolean preservePrimary) {
        int collisions = 0;
        for (Map.Entry<String, Set<String>> entry : candidates.entrySet()) {
            if (removed.contains(entry.getKey())) continue;
            if (preservePrimary && (index.containsKey(entry.getKey()) || ambiguous.containsKey(entry.getKey()))) {
                continue;
            }
            if (entry.getValue().size() == 1) {
                String id = entry.getValue().iterator().next();
                index.put(entry.getKey(), byId.get(id));
                aliasesById.computeIfAbsent(id, ignored -> new HashSet<>()).add(entry.getKey());
            }
            else if (entry.getValue().size() > 1) {
                ambiguous.put(entry.getKey(), entry.getValue().stream().map(byId::get).toList());
                collisions++;
            }
        }
        return collisions;
    }

    private static void addCandidate(Map<String, Set<String>> candidates, String alias, String id) {
        String normalized = ApplicationNames.normalize(alias);
        if (!normalized.isBlank()) candidates.computeIfAbsent(normalized, ignored -> new HashSet<>()).add(id);
    }

    private record State(boolean available, String error, List<ApplicationDefinition> applications,
                          Map<String, ApplicationDefinition> aliasIndex,
                          Map<String, List<ApplicationDefinition>> ambiguousIndex,
                          Set<String> removedAliases) {
        static State unavailable(String error) {
            return new State(false, error, List.of(), Map.of(), Map.of(), Set.of());
        }
    }
}

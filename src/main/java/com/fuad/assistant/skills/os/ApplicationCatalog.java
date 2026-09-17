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

    public ApplicationResolution resolveAmong(String target,
                                               Collection<ApplicationDefinition> candidates) {
        State current = state.get();
        if (!current.available) return ApplicationResolution.unavailable();
        Set<String> allowed = candidates == null ? Set.of() : candidates.stream()
                .filter(Objects::nonNull)
                .map(ApplicationCatalogIdentity::stableKey)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (allowed.isEmpty()) return ApplicationResolution.unknown();
        List<ApplicationDefinition> scoped = current.applications.stream()
                .filter(app -> allowed.contains(ApplicationCatalogIdentity.stableKey(app))).toList();
        return resolveAgainst(current, scoped, target);
    }

    public String catalogKey(ApplicationDefinition application) {
        return ApplicationCatalogIdentity.stableKey(Objects.requireNonNull(application));
    }

    public String normalizeTarget(String value) {
        return state.get().nameMatcher.normalizeTarget(value);
    }

    public List<ApplicationDefinition> search(String filter, boolean refreshIfUnavailable) {
        State current = state.get();
        if (!current.available && refreshIfUnavailable && refresh()) current = state.get();
        if (!current.available) return List.of();
        String normalized = current.nameMatcher.normalizeTarget(filter);
        return current.applications.stream()
                .filter(app -> normalized.isBlank()
                        || ApplicationNames.normalize(app.getDisplayName()).contains(normalized)
                        || app.getAliases().stream().anyMatch(alias ->
                        ApplicationNames.normalize(alias).contains(normalized)))
                .sorted(ApplicationCatalogIdentity.STABLE_ORDER)
                .toList();
    }

    public List<ApplicationDefinition> applications() {
        return state.get().available ? state.get().applications : List.of();
    }

    private ApplicationResolution resolveCurrent(String target) {
        State current = state.get();
        if (!current.available) return ApplicationResolution.unavailable();
        return resolveAgainst(current, current.applications, target);
    }

    private ApplicationResolution resolveAgainst(State current, List<ApplicationDefinition> candidates,
                                                 String target) {
        String normalized = current.nameMatcher.normalizeTarget(target);
        if (normalized.isBlank()) return ApplicationResolution.unknown();
        if (current.removedAliases.contains(normalized)) return ApplicationResolution.unknown();
        List<ApplicationDefinition> exactNames = candidates.stream()
                .filter(app -> ApplicationNames.normalize(app.getDisplayName()).equals(normalized)
                        || ApplicationNames.compact(app.getDisplayName())
                        .equals(ApplicationNames.compact(normalized)))
                .toList();
        if (!exactNames.isEmpty()) return resolution(exactNames);
        List<ApplicationDefinition> indexed = current.exactAliases.get(normalized);
        if (indexed != null) {
            Set<String> allowed = candidates.stream().map(ApplicationCatalogIdentity::stableKey)
                    .collect(java.util.stream.Collectors.toSet());
            List<ApplicationDefinition> scoped = indexed.stream()
                    .filter(app -> allowed.contains(ApplicationCatalogIdentity.stableKey(app))).toList();
            return scoped.isEmpty() ? ApplicationResolution.unknown() : resolution(scoped);
        }
        return resolveNatural(candidates, normalized);
    }

    private ApplicationResolution resolveNatural(List<ApplicationDefinition> applications, String normalizedTarget) {
        List<ApplicationDefinition> matches = applications.stream()
                .filter(app -> ApplicationNames.containsWholeTokenSequence(
                        app.getDisplayName(), normalizedTarget))
                .sorted(ApplicationCatalogIdentity.STABLE_ORDER)
                .toList();
        return resolution(matches);
    }

    private ApplicationResolution resolution(List<ApplicationDefinition> matches) {
        List<ApplicationDefinition> ordered = matches.stream()
                .collect(java.util.stream.Collectors.toMap(ApplicationCatalogIdentity::stableKey,
                        app -> app, (left, right) -> left, TreeMap::new))
                .values().stream().sorted(ApplicationCatalogIdentity.STABLE_ORDER).toList();
        if (ordered.size() == 1) return ApplicationResolution.found(ordered.getFirst());
        if (ordered.size() > 1) {
            return new ApplicationResolution(ApplicationResolution.Status.AMBIGUOUS, null, ordered);
        }
        return ApplicationResolution.unknown();
    }

    private State buildState(List<ApplicationDefinition> discovered, ApplicationAliasConfig config) {
        ApplicationNameMatcher nameMatcher = new ApplicationNameMatcher(config.transcriptionAliases());
        List<ApplicationDefinition> canonical = ApplicationCatalogIdentity.canonicalize(discovered);
        Map<String, ApplicationDefinition> byKey = canonical.stream().collect(
                java.util.stream.Collectors.toMap(ApplicationCatalogIdentity::stableKey, app -> app,
                        (left, right) -> left, LinkedHashMap::new));
        Map<String, ApplicationDefinition> byId = canonical.stream()
                .filter(app -> ApplicationCatalogIdentity.canonicalId(app.getId()) != null)
                .collect(java.util.stream.Collectors.toMap(
                        app -> ApplicationCatalogIdentity.canonicalId(app.getId()), app -> app));

        Map<String, Set<String>> primaryCandidates = new HashMap<>();
        Map<String, Set<String>> derivedCandidates = new HashMap<>();
        for (ApplicationDefinition app : canonical) {
            String key = ApplicationCatalogIdentity.stableKey(app);
            addCandidate(primaryCandidates, app.getDisplayName(), key);
            app.getAliases().forEach(alias -> addCandidate(derivedCandidates, alias, key));
            String withoutVersion = app.getDisplayName().replaceFirst("(?i)\\s+v?\\d+(?:[.\\-]\\d+)*.*$", "");
            if (!withoutVersion.equals(app.getDisplayName()) && withoutVersion.split("\\s+").length > 1) {
                addCandidate(derivedCandidates, withoutVersion, key);
            }
            String[] words = ApplicationNames.normalize(app.getDisplayName()).split(" ");
            if (words.length > 1) {
                StringBuilder acronym = new StringBuilder();
                for (String word : words) if (word.matches("[a-z]+")) acronym.append(word.charAt(0));
                if (acronym.length() >= 2) addCandidate(derivedCandidates, acronym.toString(), key);
            }
            for (String path : app.getProcessIdentity().executablePaths()) {
                try {
                    String filename = Path.of(path).getFileName().toString().replaceFirst("(?i)\\.exe$", "");
                    if (filename.length() >= 3) addCandidate(derivedCandidates, filename, key);
                }
                catch (Exception ignored) { }
            }
        }

        Set<String> removed = new HashSet<>();
        config.removeAliases().forEach(value -> removed.add(ApplicationNames.normalize(value)));
        Map<String, ApplicationDefinition> index = new HashMap<>();
        Map<String, List<ApplicationDefinition>> ambiguous = new HashMap<>();
        Map<String, Set<String>> aliasesByKey = new HashMap<>();
        int collisions = registerAutomaticAliases(primaryCandidates, byKey, removed, index, ambiguous, aliasesByKey,
                false);
        collisions += registerAutomaticAliases(derivedCandidates, byKey, removed, index, ambiguous, aliasesByKey,
                true);
        if (collisions > 0) {
            System.out.println("APPLICATION ALIAS -> skipped " + collisions + " collisions");
        }

        for (Map.Entry<String, String> override : config.aliases().entrySet()) {
            ApplicationDefinition target = byId.get(ApplicationCatalogIdentity.canonicalId(override.getValue()));
            if (target == null) {
                System.err.println("APPLICATION ALIAS -> unknown AppID: " + override.getValue());
                continue;
            }
            String alias = ApplicationNames.normalize(override.getKey());
            if (alias.isBlank()) continue;
            index.put(alias, target);
            ambiguous.remove(alias);
            aliasesByKey.computeIfAbsent(ApplicationCatalogIdentity.stableKey(target), ignored -> new HashSet<>())
                    .add(alias);
        }

        List<ApplicationDefinition> resolved = new ArrayList<>();
        Map<String, ApplicationDefinition> resolvedByKey = new HashMap<>();
        Map<String, String> inferredHosts = inferHostRelationships(canonical);
        for (ApplicationDefinition app : canonical) {
            String catalogKey = ApplicationCatalogIdentity.stableKey(app);
            Set<String> processNames = new HashSet<>(configured(
                    config.processNames(), app.getId(), List.of()));
            Set<String> trustedProcessNames = new HashSet<>(configured(
                    config.trustedProcessNamesWhenPathUnavailable(), app.getId(), List.of()));
            List<Set<String>> commandLineArgumentSets = configured(config.commandLineArgumentSets(),
                    app.getId(), List.<List<String>>of()).stream()
                    .filter(arguments -> arguments != null && !arguments.isEmpty())
                    .map(Set::copyOf)
                    .toList();
            List<List<String>> exactCommandLineArgumentSets = configured(config.exactCommandLineArgumentSets(),
                    app.getId(), List.<List<String>>of()).stream()
                    .filter(Objects::nonNull).map(List::copyOf).toList();
            String inferredHost = app.getId() == null ? "" : inferredHosts.getOrDefault(app.getId(), "");
            String hostApplicationId = configured(config.hostRelationships(), app.getId(), inferredHost);
            if (!hostApplicationId.isBlank()
                    && (!byId.containsKey(ApplicationCatalogIdentity.canonicalId(hostApplicationId))
                    || hostApplicationId.equalsIgnoreCase(Objects.toString(app.getId(), "")))) {
                System.err.println("APPLICATION HOST -> invalid relationship for AppID: " + app.getId());
                hostApplicationId = "";
            }
            else if (!hostApplicationId.isBlank()) {
                hostApplicationId = byId.get(
                        ApplicationCatalogIdentity.canonicalId(hostApplicationId)).getId();
            }
            List<ApplicationWindowSignature> windowSignatures = configured(
                    config.windowSignatures(), app.getId(), List.of());
            boolean windowAssociationEnabled = Boolean.TRUE.equals(configured(
                    config.windowAssociationsEnabled(), app.getId(), false));
            Set<String> resolvedAliases = new HashSet<>(app.getAliases());
            resolvedAliases.addAll(aliasesByKey.getOrDefault(catalogKey, Set.of()));
            ApplicationDefinition updated = enrichDefinitions
                    ? app.withCatalogConfiguration(resolvedAliases, processNames,
                            trustedProcessNames, commandLineArgumentSets, exactCommandLineArgumentSets,
                            hostApplicationId, windowSignatures, windowAssociationEnabled)
                    : app;
            resolved.add(updated);
            resolvedByKey.put(catalogKey, updated);
        }
        index.replaceAll((alias, old) -> resolvedByKey.get(ApplicationCatalogIdentity.stableKey(old)));
        ambiguous.replaceAll((alias, apps) -> apps.stream()
                .map(app -> resolvedByKey.get(ApplicationCatalogIdentity.stableKey(app)))
                .sorted(ApplicationCatalogIdentity.STABLE_ORDER).toList());
        Map<String, List<ApplicationDefinition>> exactAliases = new HashMap<>();
        index.forEach((alias, app) -> exactAliases.put(alias, List.of(app)));
        exactAliases.putAll(ambiguous);
        resolved.sort(ApplicationCatalogIdentity.STABLE_ORDER);
        return new State(true, null, List.copyOf(resolved), Map.copyOf(exactAliases),
                Set.copyOf(removed), nameMatcher);
    }

    private Map<String, String> inferHostRelationships(Collection<ApplicationDefinition> applications) {
        Map<String, List<ApplicationDefinition>> byPath = new HashMap<>();
        for (ApplicationDefinition application : applications) {
            if (application.getId() == null || application.getId().isBlank()) continue;
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
                ambiguous.put(entry.getKey(), entry.getValue().stream().map(byId::get)
                        .sorted(ApplicationCatalogIdentity.STABLE_ORDER).toList());
                collisions++;
            }
        }
        return collisions;
    }

    private static void addCandidate(Map<String, Set<String>> candidates, String alias, String id) {
        String normalized = ApplicationNames.normalize(alias);
        if (!normalized.isBlank()) candidates.computeIfAbsent(normalized, ignored -> new HashSet<>()).add(id);
    }

    private static <T> T configured(Map<String, T> values, String id, T fallback) {
        if (id == null) return fallback;
        T exact = values.get(id);
        if (exact != null) return exact;
        return values.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(id))
                .map(Map.Entry::getValue).findFirst().orElse(fallback);
    }

    private record State(boolean available, String error, List<ApplicationDefinition> applications,
                         Map<String, List<ApplicationDefinition>> exactAliases,
                         Set<String> removedAliases, ApplicationNameMatcher nameMatcher) {
        static State unavailable(String error) {
            return new State(false, error, List.of(), Map.of(), Set.of(),
                    new ApplicationNameMatcher(Map.of()));
        }
    }
}

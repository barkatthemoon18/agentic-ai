package com.fuad.assistant.skills.os;

import java.util.List;
import java.util.Map;

public record ApplicationAliasConfig(Map<String, String> aliases,
                                     List<String> removeAliases,
                                     Map<String, List<String>> processNames,
                                     Map<String, List<String>> trustedProcessNamesWhenPathUnavailable,
                                     Map<String, List<List<String>>> commandLineArgumentSets,
                                     Map<String, List<List<String>>> exactCommandLineArgumentSets,
                                     Map<String, String> hostRelationships,
                                     Map<String, List<ApplicationWindowSignature>> windowSignatures,
                                     Map<String, Boolean> windowAssociationsEnabled) {
    public ApplicationAliasConfig(Map<String, String> aliases, List<String> removeAliases,
                                  Map<String, List<String>> processNames) {
        this(aliases, removeAliases, processNames, Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
    }

    public ApplicationAliasConfig {
        aliases = aliases == null ? Map.of() : Map.copyOf(aliases);
        removeAliases = removeAliases == null ? List.of() : List.copyOf(removeAliases);
        processNames = copyStringLists(processNames);
        trustedProcessNamesWhenPathUnavailable = copyStringLists(trustedProcessNamesWhenPathUnavailable);
        commandLineArgumentSets = copyNestedStringLists(commandLineArgumentSets);
        exactCommandLineArgumentSets = copyExactStringLists(exactCommandLineArgumentSets);
        hostRelationships = hostRelationships == null ? Map.of() : Map.copyOf(hostRelationships);
        windowSignatures = copyWindowSignatures(windowSignatures);
        windowAssociationsEnabled = windowAssociationsEnabled == null
                ? Map.of() : Map.copyOf(windowAssociationsEnabled);
    }

    public static ApplicationAliasConfig empty() {
        return new ApplicationAliasConfig(Map.of(), List.of(), Map.of(), Map.of(), Map.of(),
                Map.of(), Map.of(), Map.of(), Map.of());
    }

    private static Map<String, List<String>> copyStringLists(Map<String, List<String>> source) {
        if (source == null) return Map.of();
        java.util.HashMap<String, List<String>> copied = new java.util.HashMap<>();
        source.forEach((key, value) -> copied.put(key, value == null ? List.of() : List.copyOf(value)));
        return Map.copyOf(copied);
    }

    private static Map<String, List<List<String>>> copyNestedStringLists(
            Map<String, List<List<String>>> source) {
        if (source == null) return Map.of();
        java.util.HashMap<String, List<List<String>>> copied = new java.util.HashMap<>();
        source.forEach((key, groups) -> {
            if (groups == null) {
                copied.put(key, List.of());
                return;
            }
            copied.put(key, groups.stream().filter(java.util.Objects::nonNull)
                    .map(List::copyOf).toList());
        });
        return Map.copyOf(copied);
    }

    private static Map<String, List<List<String>>> copyExactStringLists(
            Map<String, List<List<String>>> source) {
        if (source == null) return Map.of();
        java.util.HashMap<String, List<List<String>>> copied = new java.util.HashMap<>();
        source.forEach((key, groups) -> copied.put(key, groups == null ? List.of()
                : groups.stream().filter(java.util.Objects::nonNull).map(List::copyOf).toList()));
        return Map.copyOf(copied);
    }

    private static Map<String, List<ApplicationWindowSignature>> copyWindowSignatures(
            Map<String, List<ApplicationWindowSignature>> source) {
        if (source == null) return Map.of();
        java.util.HashMap<String, List<ApplicationWindowSignature>> copied = new java.util.HashMap<>();
        source.forEach((key, value) -> copied.put(key, value == null ? List.of() : List.copyOf(value)));
        return Map.copyOf(copied);
    }
}

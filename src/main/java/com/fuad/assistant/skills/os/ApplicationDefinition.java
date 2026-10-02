package com.fuad.assistant.skills.os;

import java.util.List;
import java.util.Set;

public record ApplicationDefinition(
        String id,
        String displayName,
        Set<String> aliases,
        List<String> openCommand,
        ApplicationProcessIdentity processIdentity) {

    public ApplicationDefinition {
        aliases = Set.copyOf(aliases);
        openCommand = List.copyOf(openCommand);
        processIdentity = processIdentity == null ? ApplicationProcessIdentity.empty() : processIdentity;
    }

    public ApplicationDefinition(String id, String displayName, List<String> openCommand, String processName) {
        this(id, displayName, Set.of(), openCommand,
                new ApplicationProcessIdentity(Set.of(), Set.of(),
                        processName == null || processName.isBlank() ? Set.of() : Set.of(processName)));
    }

    /** Compatibility accessor for the original single-process contract. */
    public String processName() {
        return processIdentity.processNames().stream().findFirst().orElse("");
    }

    public ApplicationDefinition withCatalogConfiguration(Set<String> resolvedAliases,
                                                          Set<String> configuredProcessNames,
                                                          Set<String> trustedProcessNames,
                                                          List<Set<String>> commandLineArgumentSets,
                                                          List<List<String>> exactCommandLineArgumentSets,
                                                          String hostApplicationId,
                                                          List<ApplicationWindowSignature> windowSignatures,
                                                          boolean windowAssociationEnabled) {
        return new ApplicationDefinition(id, displayName, resolvedAliases, openCommand,
                processIdentity.withConfiguration(configuredProcessNames, trustedProcessNames,
                        commandLineArgumentSets, exactCommandLineArgumentSets, hostApplicationId,
                        windowSignatures, windowAssociationEnabled));
    }
}

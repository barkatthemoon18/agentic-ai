package com.fuad.assistant.skills.os;

import lombok.Getter;

import java.util.List;
import java.util.Set;

@Getter
public class ApplicationDefinition {
    private final String id;
    private final String displayName;
    private final Set<String> aliases;
    private final List<String> openCommand;
    private final ApplicationProcessIdentity processIdentity;

    public ApplicationDefinition(String id, String displayName, List<String> openCommand, String processName) {
        this(id, displayName, Set.of(), openCommand,
                new ApplicationProcessIdentity(Set.of(), Set.of(),
                        processName == null || processName.isBlank() ? Set.of() : Set.of(processName)));
    }

    public ApplicationDefinition(String id, String displayName, Set<String> aliases,
                                 List<String> openCommand, ApplicationProcessIdentity processIdentity) {
        this.id = id;
        this.displayName = displayName;
        this.aliases = Set.copyOf(aliases);
        this.openCommand = List.copyOf(openCommand);
        this.processIdentity = processIdentity == null ? ApplicationProcessIdentity.empty() : processIdentity;
    }

    /** Compatibility accessor for the original single-process contract. */
    public String getProcessName() {
        return processIdentity.processNames().stream().findFirst().orElse("");
    }

    public ApplicationDefinition withAliasesAndProcessNames(Set<String> resolvedAliases,
                                                             Set<String> configuredProcessNames) {
        return new ApplicationDefinition(id, displayName, resolvedAliases, openCommand,
                processIdentity.withProcessNames(configuredProcessNames));
    }
}

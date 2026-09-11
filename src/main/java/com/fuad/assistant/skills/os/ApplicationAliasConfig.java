package com.fuad.assistant.skills.os;

import java.util.List;
import java.util.Map;

public record ApplicationAliasConfig(Map<String, String> aliases,
                                     List<String> removeAliases,
                                     Map<String, List<String>> processNames) {
    public ApplicationAliasConfig {
        aliases = aliases == null ? Map.of() : Map.copyOf(aliases);
        removeAliases = removeAliases == null ? List.of() : List.copyOf(removeAliases);
        processNames = processNames == null ? Map.of() : Map.copyOf(processNames);
    }

    public static ApplicationAliasConfig empty() {
        return new ApplicationAliasConfig(Map.of(), List.of(), Map.of());
    }
}

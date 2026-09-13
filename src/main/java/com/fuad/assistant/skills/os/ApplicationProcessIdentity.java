package com.fuad.assistant.skills.os;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record ApplicationProcessIdentity(Set<String> executablePaths,
                                         Set<String> packageRoots,
                                         Set<String> processNames,
                                         Set<String> trustedProcessNamesWhenPathUnavailable,
                                         List<Set<String>> commandLineArgumentSets,
                                         List<List<String>> exactCommandLineArgumentSets,
                                         String hostApplicationId,
                                         List<ApplicationWindowSignature> windowSignatures,
                                         boolean windowAssociationEnabled) {
    public ApplicationProcessIdentity(Set<String> executablePaths, Set<String> packageRoots,
                                      Set<String> processNames) {
        this(executablePaths, packageRoots, processNames, Set.of(), List.of(), List.of(), "", List.of(), false);
    }

    public ApplicationProcessIdentity(Set<String> executablePaths, Set<String> packageRoots,
                                      Set<String> processNames, Set<String> trustedProcessNamesWhenPathUnavailable,
                                      List<Set<String>> commandLineArgumentSets) {
        this(executablePaths, packageRoots, processNames, trustedProcessNamesWhenPathUnavailable,
                commandLineArgumentSets, List.of(), "", List.of(), false);
    }

    public ApplicationProcessIdentity {
        executablePaths = executablePaths == null ? Set.of() : Set.copyOf(executablePaths);
        packageRoots = packageRoots == null ? Set.of() : Set.copyOf(packageRoots);
        processNames = processNames == null ? Set.of() : Set.copyOf(processNames);
        trustedProcessNamesWhenPathUnavailable = trustedProcessNamesWhenPathUnavailable == null
                ? Set.of() : Set.copyOf(trustedProcessNamesWhenPathUnavailable);
        if (commandLineArgumentSets == null) {
            commandLineArgumentSets = List.of();
        }
        else {
            List<Set<String>> copied = new ArrayList<>();
            for (Set<String> arguments : commandLineArgumentSets) {
                if (arguments != null && !arguments.isEmpty()) copied.add(Set.copyOf(arguments));
            }
            commandLineArgumentSets = List.copyOf(copied);
        }
        if (exactCommandLineArgumentSets == null) {
            exactCommandLineArgumentSets = List.of();
        }
        else {
            List<List<String>> copied = new ArrayList<>();
            for (List<String> arguments : exactCommandLineArgumentSets) {
                if (arguments != null) copied.add(List.copyOf(arguments));
            }
            exactCommandLineArgumentSets = List.copyOf(copied);
        }
        hostApplicationId = hostApplicationId == null ? "" : hostApplicationId.trim();
        windowSignatures = windowSignatures == null ? List.of() : List.copyOf(windowSignatures);
    }

    public static ApplicationProcessIdentity empty() {
        return new ApplicationProcessIdentity(Set.of(), Set.of(), Set.of(), Set.of(), List.of(),
                List.of(), "", List.of(), false);
    }

    public boolean isEmpty() {
        return executablePaths.isEmpty() && packageRoots.isEmpty() && processNames.isEmpty()
                && trustedProcessNamesWhenPathUnavailable.isEmpty() && commandLineArgumentSets.isEmpty()
                && exactCommandLineArgumentSets.isEmpty() && hostApplicationId.isBlank()
                && windowSignatures.isEmpty();
    }

    public ApplicationProcessIdentity withConfiguration(Set<String> additionalNames,
                                                        Set<String> additionalTrustedNames,
                                                        List<Set<String>> additionalArgumentSets,
                                                        List<List<String>> additionalExactArgumentSets,
                                                        String configuredHostApplicationId,
                                                        List<ApplicationWindowSignature> configuredWindowSignatures,
                                                        boolean configuredWindowAssociationEnabled) {
        Set<String> merged = new HashSet<>(processNames);
        merged.addAll(additionalNames);
        Set<String> trusted = new HashSet<>(trustedProcessNamesWhenPathUnavailable);
        trusted.addAll(additionalTrustedNames);
        List<Set<String>> arguments = new ArrayList<>(commandLineArgumentSets);
        arguments.addAll(additionalArgumentSets);
        List<List<String>> exactArguments = new ArrayList<>(exactCommandLineArgumentSets);
        exactArguments.addAll(additionalExactArgumentSets);
        String hostId = configuredHostApplicationId == null || configuredHostApplicationId.isBlank()
                ? hostApplicationId : configuredHostApplicationId;
        List<ApplicationWindowSignature> windows = new ArrayList<>(windowSignatures);
        windows.addAll(configuredWindowSignatures);
        return new ApplicationProcessIdentity(executablePaths, packageRoots, merged, trusted, arguments,
                exactArguments, hostId, windows,
                windowAssociationEnabled || configuredWindowAssociationEnabled);
    }
}

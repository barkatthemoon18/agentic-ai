package com.fuad.assistant.skills.os;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class ApplicationNameMatcher {
    private final List<Replacement> replacements;

    ApplicationNameMatcher(Map<String, String> transcriptionAliases) {
        Map<String, List<SourceAlias>> normalized = new HashMap<>();
        Map<String, String> source = transcriptionAliases == null ? Map.of() : transcriptionAliases;
        source.forEach((rawKey, rawValue) -> {
            String key = ApplicationNames.normalize(rawKey);
            String value = ApplicationNames.normalize(rawValue);
            if (key.isBlank() || value.isBlank()) {
                throw new IllegalArgumentException(
                        "Application transcription aliases must not normalize to blank values");
            }
            normalized.computeIfAbsent(key, ignored -> new ArrayList<>())
                    .add(new SourceAlias(rawKey, value));
        });
        normalized.entrySet().stream().filter(entry -> entry.getValue().size() > 1)
                .sorted(Map.Entry.comparingByKey()).findFirst().ifPresent(entry -> {
                    List<String> conflicting = entry.getValue().stream().map(SourceAlias::rawKey)
                            .sorted(Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)
                                    .thenComparing(Comparator.nullsLast(Comparator.naturalOrder())))
                            .toList();
                    throw new IllegalArgumentException(
                            "Application transcription alias collision after normalization: "
                                    + String.join(" / ", conflicting) + " -> " + entry.getKey());
                });
        replacements = normalized.entrySet().stream()
                .map(entry -> new Replacement(tokens(entry.getKey()),
                        tokens(entry.getValue().getFirst().value())))
                .sorted(Comparator.<Replacement>comparingInt(replacement -> replacement.source().size())
                        .reversed().thenComparing(replacement -> String.join(" ", replacement.source())))
                .toList();
    }

    String normalizeTarget(String value) {
        List<String> input = tokens(ApplicationNames.normalize(value));
        if (input.isEmpty() || replacements.isEmpty()) {
            return String.join(" ", input);
        }
        List<String> result = new ArrayList<>();
        for (int index = 0; index < input.size();) {
            Replacement replacement = matchingReplacement(input, index);
            if (replacement == null) {
                result.add(input.get(index++));
            }
            else {
                result.addAll(replacement.target());
                index += replacement.source().size();
            }
        }
        return String.join(" ", result);
    }

    private Replacement matchingReplacement(List<String> input, int offset) {
        for (Replacement replacement : replacements) {
            if (offset + replacement.source().size() <= input.size()
                    && input.subList(offset, offset + replacement.source().size())
                    .equals(replacement.source())) {
                return replacement;
            }
        }
        return null;
    }

    private static List<String> tokens(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.asList(value.split(" "));
    }

    private record SourceAlias(String rawKey, String value) {
        private SourceAlias {
            Objects.requireNonNull(rawKey);
            Objects.requireNonNull(value);
        }
    }

    private record Replacement(List<String> source, List<String> target) {
        private Replacement {
            source = List.copyOf(source);
            target = List.copyOf(target);
        }
    }
}

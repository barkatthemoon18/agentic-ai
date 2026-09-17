package com.fuad.assistant.skills.os;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;

final class ApplicationNames {
    private ApplicationNames() { }

    static String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    static String compact(String value) {
        return normalize(value).replace(" ", "");
    }

    static boolean startsWithWholeTokens(String candidate, String prefix) {
        String normalizedCandidate = normalize(candidate);
        String normalizedPrefix = normalize(prefix);
        return !normalizedPrefix.isBlank()
                && (normalizedCandidate.equals(normalizedPrefix)
                || normalizedCandidate.startsWith(normalizedPrefix + " "));
    }

    static boolean containsWholeTokenSequence(String candidate, String target) {
        String normalizedCandidate = normalize(candidate);
        String normalizedTarget = normalize(target);
        if (normalizedCandidate.isBlank() || normalizedTarget.isBlank()) return false;
        java.util.List<String> candidateTokens = Arrays.asList(normalizedCandidate.split(" "));
        java.util.List<String> targetTokens = Arrays.asList(normalizedTarget.split(" "));
        if (targetTokens.size() > candidateTokens.size()) return false;
        for (int index = 0; index <= candidateTokens.size() - targetTokens.size(); index++) {
            if (candidateTokens.subList(index, index + targetTokens.size()).equals(targetTokens)) {
                return true;
            }
        }
        return false;
    }
}

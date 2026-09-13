package com.fuad.assistant.skills.os;

import java.text.Normalizer;
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
}

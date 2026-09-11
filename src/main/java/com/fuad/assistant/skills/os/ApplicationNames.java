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
}

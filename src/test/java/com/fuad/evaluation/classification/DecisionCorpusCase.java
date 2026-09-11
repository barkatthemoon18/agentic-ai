package com.fuad.evaluation.classification;

import java.util.List;
import java.util.Locale;

public record DecisionCorpusCase(String id, String query, String expected,
                                 List<String> tags, String rationale,
                                 String inheritedBackend) {
    public String normalizedExpected() {
        return expected.trim().toLowerCase(Locale.ROOT);
    }

    public String normalizedInheritedBackend() {
        return inheritedBackend == null ? null
                : inheritedBackend.trim().toLowerCase(Locale.ROOT);
    }
}

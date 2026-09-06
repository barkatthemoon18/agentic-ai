package com.fuad.model;

import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public final class LocalModelOutput {
    private LocalModelOutput() {
    }

    public static String requireModelId(String model) {
        String normalized = Objects.requireNonNull(model, "model cannot be null").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("model cannot be empty");
        }
        return normalized;
    }

    public static String extractLeadingLabel(String output, Set<String> allowedLabels,
                                             String classificationName) {
        String normalized = Objects.requireNonNull(output, "output cannot be null")
                .trim().toLowerCase(Locale.ROOT);
        Set<String> labels = Set.copyOf(Objects.requireNonNull(
                allowedLabels, "allowedLabels cannot be null"));
        if (labels.isEmpty()) {
            throw new IllegalArgumentException("allowedLabels cannot be empty");
        }
        normalized = normalized.replaceFirst("^(?:```(?:text)?\\s*|[\\\"'`*]+)", "");

        for (String label : labels.stream()
                .sorted(Comparator.comparingInt(String::length).reversed()).toList()) {
            if (normalized.startsWith(label) && hasBoundary(normalized, label.length())) {
                return label;
            }
        }
        throw new IllegalStateException("Unknown " + classificationName + ": " + output);
    }

    public static String firstContractLine(String output) {
        String normalized = Objects.requireNonNull(output, "output cannot be null").trim();
        normalized = normalized.replaceFirst("^(?:```(?:text)?\\s*|[\\\"'`*]+)", "");
        int lineEnd = normalized.indexOf('\n');
        String firstLine = (lineEnd >= 0 ? normalized.substring(0, lineEnd) : normalized).trim();
        return firstLine.replaceFirst("[\\\"'`*]+$", "").trim();
    }

    private static boolean hasBoundary(String output, int labelLength) {
        if (output.length() == labelLength) {
            return true;
        }
        char next = output.charAt(labelLength);
        return Character.isWhitespace(next) || "\"'`*):;,.".indexOf(next) >= 0;
    }
}

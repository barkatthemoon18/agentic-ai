package com.fuad.assistant.session;

import com.fuad.enums.ConversationControl;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public class ConversationControlDetector {
    private static final double FORGET_THRESHOLD = 0.72;
    private static final Pattern EXPLICIT_CLOSE_PATTERN =
            Pattern.compile("^(?:ares\\s+)?" +
                    "(?:por favor\\s+)?" +
                    "(?:cancela|cancelar|termina|terminar|cierra|cerrar)\\s+" +
                    "(?:la\\s+)?" +
                    "conversacion" +
                    "(?:\\s+por favor)?$");
    private static final Pattern NATURAL_CLOSE_PATTERN =
            Pattern.compile("^(?:ares\\s+)?" +
                    "(?:nada\\s+)?" +
                    "(?:olvidalo|dejalo)" +
                    "$");
    private static final Pattern FINISHED_PATTERN =
            Pattern.compile(
                    "^(?:ares\\s+)?" +
                            "(?:eso es todo|ya esta)" +
                            "$");

    public ConversationControl detect(String text) {
        String normalized = normalize(text);
        if (EXPLICIT_CLOSE_PATTERN.matcher(normalized).matches()) {
            return ConversationControl.CLOSE;
        }
        if (NATURAL_CLOSE_PATTERN.matcher(normalized).matches()) {
            return ConversationControl.CLOSE;
        }
        if (FINISHED_PATTERN.matcher(normalized).matches()) {
            return ConversationControl.CLOSE;
        }
        if (isForgetVariation(normalized)) {
            return ConversationControl.CLOSE;
        }
        return ConversationControl.NONE;
    }

    private boolean isForgetVariation(String text) {
        String candidate = text;

        if (candidate.startsWith("ares ")) {
            candidate = candidate.substring(5);
        }
        if (candidate.startsWith("nada ")) {
            candidate = candidate.substring(5);
        }
        if (candidate.contains(" ") || candidate.length() > 12) {
            return false;
        }
        return similarity(candidate, "olvidalo") >= FORGET_THRESHOLD;
    }

    private double similarity(String first, String second) {
        if (first.equals(second)) {
            return 1.0;
        }
        if (first.isEmpty() || second.isEmpty()) {
            return 0.0;
        }
        int distance = levenshtein(first, second);
        int maxLength = Math.max(first.length(), second.length());
        return 1.0 - ((double) distance / maxLength);
    }

    private int levenshtein(String first, String second) {
        int[] previous = new int[second.length() + 1];
        int[] current = new int[second.length() + 1];

        for (int j = 0; j <= second.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= first.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= second.length(); j++) {
                int substitutionCost = first.charAt(i - 1) == second.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + substitutionCost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[second.length()];
    }

    private String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^\\p{L}\\p{N}\\s]", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }
}

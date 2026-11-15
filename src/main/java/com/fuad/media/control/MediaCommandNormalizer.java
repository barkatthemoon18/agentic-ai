package com.fuad.media.control;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public final class MediaCommandNormalizer {
    private static final Pattern DIRECT_VERB_COMMA = Pattern.compile("^(pausa|reanuda|continua|pon en pausa" +
                    "|sigue reproduciendo)\\s*,\\s*");
    private static final Pattern SCOPED_COMMA = Pattern.compile("\\s*,\\s*(en|de|del|desde|para|con|usando)" +
                    "\\s+(tidal|spotify|vlc)$");

    private MediaCommandNormalizer() {
        /* Empty intentionally */
    }

    public static String normalize(String command) {
        String normalized;

        if (command == null || command.isBlank()) {
            return "";
        }
        normalized = Normalizer.normalize(command, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).strip().replaceAll("\\s+", " ")
                .replaceAll("^[¡¿!]+|[.!?]+$", "").strip();
        normalized = normalized.replaceFirst("^por favor,?\\s+", "")
                .replaceFirst("(?:,\\s*|\\s+)por favor$", "").strip();
        normalized = DIRECT_VERB_COMMA.matcher(normalized).replaceFirst("$1 ");
        normalized = SCOPED_COMMA.matcher(normalized).replaceFirst(" $1 $2");
        return normalized.strip();
    }
}

package com.fuad.media.control;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Recognizes a small set of complete, unambiguous playback commands.
 * An empty detection is inconclusive, not a negative classification.
 * Player names identify the playback domain only: actions still address
 * the provider's active session, without application targeting.
 */
public final class MediaControlCommandDetector {
    private static final String TRACK = "(?:cancion|pista|tema)";
    private static final String PLAYBACK_TARGET =
            "(?:(?:la )?(?:musica|cancion|pista|reproduccion)|tidal|spotify|vlc)";
    private static final Map<MediaAction, Pattern> COMMANDS = Map.of(
            MediaAction.NEXT, Pattern.compile("(?:siguiente " + TRACK
                    + "|pon la siguiente " + TRACK
                    + "|(?:cambia|pasa|salta) a la siguiente " + TRACK
                    + "|salta esta " + TRACK + ")"),
            MediaAction.PREVIOUS, Pattern.compile("(?:" + TRACK + " anterior"
                    + "|pon la " + TRACK + " anterior"
                    + "|(?:vuelve|regresa|retrocede) a la " + TRACK + " anterior)"),
            MediaAction.PAUSE, Pattern.compile("(?:pausa|pon en pausa) " + PLAYBACK_TARGET),
            MediaAction.RESUME, Pattern.compile("(?:reanuda|continua|sigue reproduciendo) " + PLAYBACK_TARGET));

    private static final List<Pattern> CONTRADICTIONS = List.of(
            Pattern.compile("no (?:pauses|reanudes|continues|saltes|cambies) " + PLAYBACK_TARGET),
            Pattern.compile("que " + TRACK + " (?:esta sonando|se esta reproduciendo)"),
            Pattern.compile("(?:explicame )?como (?:pausar|reanudar) " + PLAYBACK_TARGET),
            Pattern.compile("(?:sube|baja|aumenta|reduce) el volumen"
                    + "(?: de (?:tidal|spotify|vlc|ares|windows))?"),
            Pattern.compile("(?:silencia|mutea|desmutea) (?:tidal|spotify|vlc|tu voz)"),
            Pattern.compile("(?:abre|cierra|inicia|reinicia|termina) (?:tidal|spotify|vlc)"),
            Pattern.compile("(?:activa|desactiva) (?:shuffle|repeat|la reproduccion aleatoria|la repeticion)"),
            // Only quoted titles and this explicitly known selection request are
            // conclusive. Never infer a title from an arbitrary "pon ..." phrase.
            Pattern.compile("(?:pon|reproduce) (?:comfortably numb|[\"“][^\"“”]+[\"”])"));

    public Optional<MediaAction> detect(String command) {
        String normalized = normalize(command);
        for (var entry : COMMANDS.entrySet()) {
            if (entry.getValue().matcher(normalized).matches()) {
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }

    /** True only for an explicitly recognized contradiction; unknown input is false. */
    public boolean definitelyNotMediaControl(String command) {
        String normalized = normalize(command);
        return CONTRADICTIONS.stream().anyMatch(pattern -> pattern.matcher(normalized).matches());
    }

    private String normalize(String command) {
        if (command == null) {
            return "";
        }
        return Normalizer.normalize(command, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim()
                .replaceAll("\\s+", " ")
                .replaceAll("^[¡¿]+|[.!?]+$", "")
                .trim()
                .replaceFirst("^por favor,? ", "");
    }
}

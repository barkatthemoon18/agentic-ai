package com.fuad.assistant.skills.media;

import com.fuad.media.control.MediaControlCommandDetector;
import com.fuad.media.control.MediaTarget;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

public final class MediaCommandTargetResolver {
    private static final Map<String, String> PLAYERS = Map.of(
            "tidal", "TIDAL",
            "spotify", "Spotify",
            "vlc", "VLC");
    private static final Set<String> GENERIC_OBJECTS = Set.of(
            "musica", "cancion", "pista",
            "tema", "reproduccion", "audio");
    private static final Pattern UNSAFE_CONTEXT = Pattern.compile(
            "\\b(?:no|nunca|jamas|cuando|si|salvo|pero"
                    + "|aunque|manana|despues|luego|y)\\b");
    private static final Pattern KNOWN_MENTION = Pattern.compile(
            "\\b(?:tidal|spotify|vlc)\\b");
    private static final Pattern DIRECT = Pattern.compile(
            "^(?:pausa|reanuda|continua|pon en pausa"
                    + "|sigue reproduciendo)\\s+([a-z][a-z0-9._-]*)$");
    private static final Pattern SCOPED = Pattern.compile(
            "^(.+)\\s+(?:en|de|del|desde|para|con|usando)"
                    + "\\s+([a-z][a-z0-9._-]*)$");
    private static final Pattern DIRECT_VERB = Pattern.compile(
            "^(?:pausa|reanuda|continua|pon en pausa"
                    + "|sigue reproduciendo)\\s+.+$");
    private static final Pattern EXTRA_TRACK_TARGET = Pattern.compile(
            ".*\\b(?:cancion|pista|tema)\\s+"
                    + "(?!anterior$|siguiente$|actual$)"
                    + "[a-z][a-z0-9._-]*$");
    private final MediaControlCommandDetector actionDetector;

    public MediaCommandTargetResolver() {
        this(new MediaControlCommandDetector());
    }

    public MediaCommandTargetResolver(MediaControlCommandDetector actionDetector) {
        this.actionDetector = Objects.requireNonNull(actionDetector, "actionDetector cannot be null");
    }

    public Optional<MediaTarget> resolve(String command) {
        String normalized = normalize(command);
        if (normalized.isBlank() || UNSAFE_CONTEXT.matcher(normalized).find()) {
            return Optional.empty();
        }
        if (normalized.equalsIgnoreCase("ponlo en pausa") || normalized.equalsIgnoreCase("dale de nuevo")) {
            return Optional.of(MediaTarget.active());
        }
        var scoped = SCOPED.matcher(normalized);
        if (scoped.matches()) {
            String baseCommand = scoped.group(1);
            String player = canonicalPlayer(scoped.group(2));
            if (player == null || KNOWN_MENTION.matcher(baseCommand).find() || actionDetector.detect(baseCommand).isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(MediaTarget.application(player));
        }
        var direct = DIRECT.matcher(normalized);
        if (direct.matches()) {
            String object = direct.group(1);
            String player = canonicalPlayer(object);
            if (player != null) {
                return Optional.of(MediaTarget.application(player));
            }
            return GENERIC_OBJECTS.contains(object) ? Optional.of(MediaTarget.active()) : Optional.empty();
        }
        if (KNOWN_MENTION.matcher(normalized).find()) {
            return Optional.empty();
        }
        if (DIRECT_VERB.matcher(normalized).matches() && actionDetector.detect(normalized).isEmpty()) {
            return Optional.empty();
        }
        if (EXTRA_TRACK_TARGET.matcher(normalized).matches() && actionDetector.detect(normalized).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(MediaTarget.active());
    }

    private static String normalize(String command) {
        if (command == null) {
            return "";
        }
        return Normalizer.normalize(command, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).strip().replaceAll("\\s+", " ")
                .replaceAll("^[¡¿]+|[.!?]+$", "").strip()
                .replaceFirst("^por favor,?\\s*", "")
                .replaceFirst(",?\\s+por favor$", "").strip();
    }

    private static String canonicalPlayer(String value) {
        return PLAYERS.get(value.replaceFirst("\\.exe$", ""));
    }
}

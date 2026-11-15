package com.fuad.assistant.skills.media;

import com.fuad.media.control.MediaCommandNormalizer;
import com.fuad.media.control.MediaControlCommandDetector;
import com.fuad.media.control.MediaTarget;

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
    private static final Set<String> SAFE_GENERIC_ALIASES = Set.of("vuelve atras", "pon la siguiente", "pon la anterior",
            "siguiente", "anterior", "continua");
    private static final Pattern UNSUPPORTED_SEPARATORS = Pattern.compile("[,;:!?]");
    private static final Pattern UNSAFE_CONTEXT = Pattern.compile(
            "\\b(?:no|nunca|jamas|cuando|si|salvo|pero"
                    + "|aunque|manana|despues|luego|y)\\b");
    private static final Pattern KNOWN_MENTION = Pattern.compile(
            "\\b(?:tidal|spotify|vlc)\\b");
    private static final Pattern DIRECT = Pattern.compile(
            "^(?:pausa|reanuda|continua|pon en pausa"
                    + "|sigue reproduciendo)\\s+([a-z][a-z0-9._-]*)$");
    private static final Pattern EXPLICIT_SCOPED = Pattern.compile(
            "^(.+)\\s+(?:en|de|del|desde|para|con|usando)\\s+(.+)$");
    private final MediaControlCommandDetector actionDetector;

    public MediaCommandTargetResolver() {
        this(new MediaControlCommandDetector());
    }

    public MediaCommandTargetResolver(MediaControlCommandDetector actionDetector) {
        this.actionDetector = Objects.requireNonNull(actionDetector, "actionDetector cannot be null");
    }

    public Optional<MediaTarget> resolve(String command) {
        String normalized = MediaCommandNormalizer.normalize(command);
        if (normalized.isBlank() || UNSAFE_CONTEXT.matcher(normalized).find() || UNSUPPORTED_SEPARATORS.matcher(normalized).find()) {
            return Optional.empty();
        }
        if (normalized.equalsIgnoreCase("ponlo en pausa") || normalized.equalsIgnoreCase("dale de nuevo")) {
            return Optional.of(MediaTarget.active());
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
        String prefix = normalized.startsWith("pon en pausa ") ? "pon en pausa " : "";
        String candidate = normalized.substring(prefix.length());
        var scoped = EXPLICIT_SCOPED.matcher(candidate);
        if (scoped.matches()) {
            String baseCommand = prefix + scoped.group(1);
            String destination = scoped.group(2);
            String player = canonicalPlayer(destination);
            if (player == null || KNOWN_MENTION.matcher(baseCommand).find() || actionDetector.detect(baseCommand).isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(MediaTarget.application(player));
        }
        if (KNOWN_MENTION.matcher(normalized).find()) {
            return Optional.empty();
        }
        if (actionDetector.detect(normalized).isPresent() || SAFE_GENERIC_ALIASES.contains(normalized)) {
            return Optional.of(MediaTarget.active());
        }
        return Optional.empty();
    }

    private static String canonicalPlayer(String value) {
        return PLAYERS.get(value.replaceFirst("\\.exe$", ""));
    }
}

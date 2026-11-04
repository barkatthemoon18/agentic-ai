package com.fuad.assistant.skills.media;

import com.fuad.media.control.MediaTarget;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MediaCommandTargetResolver {
    private static final Map<String, String> PLAYERS = Map.of(
            "tidal", "TIDAL",
            "spotify", "Spotify",
            "vlc", "VLC");
    private static final Pattern SCOPED_KNOWN_PLAYER = Pattern.compile(
            "\\b(?:en|desde|de|del)\\s+(tidal|spotify|vlc)\\b");
    private static final Pattern UNSAFE_CONTEXT = Pattern.compile(
            "\\b(no|nunca|jamas|cuando|si|salvo|pero|aunque|manana|despues|luego|y)\\b");
    private static final Pattern SCOPED_TARGET = Pattern.compile(
            "\\b(?:en|desde|de|del)\\s+(?!pausa\\b)[a-z][a-z0-9._-]*");
    private static final Pattern DIRECT_COMMAND = Pattern.compile(
            "^(?:pausa|reanuda|continua|pon en pausa|sigue reproduciendo)\\s+.+$");
    private static final Pattern GENERIC_COMMAND = Pattern.compile(
            "^(?:pausa|reanuda|continua|pon en pausa|sigue reproduciendo)"
                    + "\\s+(?:(?:la|el)\\s+)?"
                    + "(?:musica|cancion|pista|tema|reproduccion|audio|tidal|spotify|vlc)$");

    public Optional<MediaTarget> resolve(String command) {
        String normalized = normalize(command);
        if (normalized.isBlank() || UNSAFE_CONTEXT.matcher(normalized).find()) {
            return Optional.empty();
        }

        Set<String> scopedMatches = new LinkedHashSet<>();
        Matcher scopedMatcher = SCOPED_KNOWN_PLAYER.matcher(normalized);
        while (scopedMatcher.find()) {
            scopedMatches.add(PLAYERS.get(scopedMatcher.group(1)));
        }
        if (scopedMatches.size() == 1) {
            return Optional.of(MediaTarget.application(scopedMatches.iterator().next()));
        }
        if (scopedMatches.size() > 1) {
            return Optional.empty();
        }

        boolean unknownScopedTarget = SCOPED_TARGET.matcher(normalized).find();
        boolean unknownDirectTarget = DIRECT_COMMAND.matcher(normalized).matches()
                && !GENERIC_COMMAND.matcher(normalized).matches();
        if (unknownScopedTarget || unknownDirectTarget) {
            return Optional.empty();
        }

        return Optional.of(MediaTarget.active());
    }

    private static String normalize(String command) {
        if (command == null) {
            return "";
        }
        return Normalizer.normalize(command, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).strip().replaceAll("\\s+", " ").strip()
                .replaceFirst("^por favor,?", "");
    }
}

package com.fuad.assistant.skills.media;

import com.fuad.assistant.AssistantResult;
import com.fuad.assistant.skills.Skill;
import com.fuad.media.control.MediaAction;
import com.fuad.media.control.MediaActionHandler;

import java.util.Objects;

public final class MediaControlSkill implements Skill {
    private final MediaControlParser parser;
    private final MediaActionHandler mediaActionHandler;

    public MediaControlSkill(MediaControlParser parser, MediaActionHandler mediaActionHandler) {
        this.parser = Objects.requireNonNull(parser);
        this.mediaActionHandler = Objects.requireNonNull(mediaActionHandler);
    }

    @Override
    public AssistantResult execute(String command) {
        MediaControlIntent intent = Objects.requireNonNull(parser.parse(command), "parser result cannot be null");

        MediaAction action = switch (intent) {
            case NEXT -> MediaAction.NEXT;
            case PREVIOUS -> MediaAction.PREVIOUS;
            case PAUSE -> MediaAction.PAUSE;
            case RESUME -> MediaAction.RESUME;
            case UNSUPPORTED -> null;
        };

        if (action == null) {
            return new AssistantResult("No interpreté ese control multimedia.");
        }
        if (!mediaActionHandler.submit(action)) {
            return new AssistantResult("No pude controlar la reproducción multimedia.");
        }
        return switch (intent) {
            case NEXT -> new AssistantResult("Siguiente.");
            case PREVIOUS -> new AssistantResult("Anterior.");
            case PAUSE -> new AssistantResult("Reproducción pausada.");
            case RESUME -> new AssistantResult("Reproducción reanudada.");
            case UNSUPPORTED -> throw new IllegalStateException();
        };
    }
}

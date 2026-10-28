package com.fuad.assistant.skills.media;

import com.fuad.assistant.AssistantResult;
import com.fuad.assistant.skills.Skill;
import com.fuad.media.control.MediaAction;
import com.fuad.media.control.MediaActionExecutor;
import com.fuad.media.control.MediaActionHandler;
import com.fuad.media.control.MediaActionResult;

import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class MediaControlSkill implements Skill {
    private final MediaControlParser parser;
    private final MediaActionExecutor mediaActionExecutor;

    public MediaControlSkill(MediaControlParser parser,MediaActionExecutor mediaActionExecutor) {
        this.parser = Objects.requireNonNull(parser);
        this.mediaActionExecutor = Objects.requireNonNull(mediaActionExecutor);
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
        try {
            MediaActionResult result = mediaActionExecutor.executeAsync(action).toCompletableFuture().get(2,
                    TimeUnit.SECONDS);
            return switch (result.status()) {
                case ACKNOWLEDGED -> successResponse(intent);
                case REJECTED -> new AssistantResult("No pude aplicar el control multimedia");
                case FAILED -> new AssistantResult("Ocurrió un error al controlar la reproducción");
                case CLOSED -> new AssistantResult("El control multimedia no está disponible");
            };
        }
        catch (TimeoutException e) {
            return new AssistantResult("No pude confirmar el resultado del comando");
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new AssistantResult("La operación multimedia fue interrumpida");
        }
        catch (ExecutionException e) {
            return new AssistantResult("Ocurrió un error al ejecutar el comando");
        }
    }

    private AssistantResult successResponse(MediaControlIntent intent) {
        return switch (intent) {
            case NEXT -> new AssistantResult("Siguiente.");
            case PREVIOUS -> new AssistantResult("Anterior.");
            case PAUSE -> new AssistantResult("Reproducción pausada.");
            case RESUME -> new AssistantResult("Reproducción reanudada.");
            case UNSUPPORTED -> throw new IllegalStateException();
        };
    }
}

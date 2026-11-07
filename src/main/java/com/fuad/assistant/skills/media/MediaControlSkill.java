package com.fuad.assistant.skills.media;

import com.fuad.assistant.AssistantResult;
import com.fuad.assistant.skills.Skill;
import com.fuad.media.control.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.*;

import static com.fuad.logging.LogCategory.MEDIA;

public final class MediaControlSkill implements Skill {
    private static final Logger LOGGER = LoggerFactory.getLogger(MediaControlSkill.class);
    private final MediaControlParser parser;
    private final MediaActionExecutor mediaActionExecutor;
    private final MediaCommandTargetResolver targetResolver;

    public MediaControlSkill(MediaControlParser parser,MediaActionExecutor mediaActionExecutor) {
        this(parser, mediaActionExecutor, new MediaCommandTargetResolver());
    }

    public MediaControlSkill(MediaControlParser parser, MediaActionExecutor mediaActionExecutor,
                             MediaCommandTargetResolver targetResolver) {
        this.parser = Objects.requireNonNull(parser);
        this.mediaActionExecutor = Objects.requireNonNull(mediaActionExecutor);
        this.targetResolver = Objects.requireNonNull(targetResolver);
    }

    @Override
    public AssistantResult execute(String command) {
        CompletableFuture<MediaActionResult> pending = null;
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
            Optional<MediaTarget> resolvedTarget = targetResolver.resolve(command);
            if (resolvedTarget.isEmpty()) {
                LOGGER.warn(MEDIA, "event=MEDIA_TARGET_REJECTED reason=UNRESOLVED");
                return new AssistantResult("No pude identificar de forma segura el reproductor");
            }
            MediaTarget target = resolvedTarget.orElseThrow();
            CompletionStage<MediaActionResult> execution = target instanceof MediaTarget.Active ?
                    mediaActionExecutor.executeAsync(action) : mediaActionExecutor.executeAsync(new MediaControlRequest(action, target));
            pending = execution.toCompletableFuture();
            MediaActionResult result = execution.toCompletableFuture().get(2, TimeUnit.SECONDS);
            return switch (result.status()) {
                case ACKNOWLEDGED -> successResponse(intent);
                case REJECTED -> new AssistantResult("No pude aplicar el control multimedia");
                case FAILED -> new AssistantResult("Ocurrió un error al controlar la reproducción");
                case CLOSED -> new AssistantResult("El control multimedia no está disponible");
            };
        }
        catch (TimeoutException e) {
            pending.cancel(false);
            return new AssistantResult("No pude confirmar el resultado del comando");
        }
        catch (InterruptedException e) {
            pending.cancel(false);
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

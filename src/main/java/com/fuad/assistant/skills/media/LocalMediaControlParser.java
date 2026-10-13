package com.fuad.assistant.skills.media;

import com.fuad.config.AppConfig;
import com.fuad.media.control.MediaAction;
import com.fuad.media.control.MediaControlCommandDetector;
import com.fuad.model.LocalModelOutput;
import com.openai.client.OpenAIClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;

import java.util.Locale;
import java.util.Objects;

public class LocalMediaControlParser implements MediaControlParser {
    private static final String SYSTEM_PROMPT = """
            Eres un parser restringido de controles de reproducción multimedia
            para un asistente llamado Ares.

            El usuario habla español.

            Tu única tarea es identificar la acción multimedia solicitada.

            Devuelve exclusivamente UNA de estas etiquetas:

            next
            previous
            pause
            resume
            unsupported

            ==================================================
            NEXT
            ==================================================

            Usa next cuando el usuario quiere avanzar a la siguiente pista,
            canción o elemento multimedia.

            Ejemplos:

            "Siguiente canción"
            -> next

            "Pon la siguiente"
            -> next

            "Cambia a la siguiente canción"
            -> next

            "Salta esta canción"
            -> next

            "Pasa a la siguiente pista"
            -> next

            ==================================================
            PREVIOUS
            ==================================================

            Usa previous cuando el usuario quiere volver a la pista o canción
            anterior.

            Ejemplos:

            "Canción anterior"
            -> previous

            "Pon la anterior"
            -> previous

            "Vuelve a la canción anterior"
            -> previous

            "Vuelve atrás"
            -> previous

            "Retrocede a la anterior"
            -> previous

            ==================================================
            PAUSE
            ==================================================

            Usa pause cuando el usuario quiere detener temporalmente la
            reproducción actual.

            Ejemplos:

            "Pausa la música"
            -> pause

            "Pausa la canción"
            -> pause

            "Ponlo en pausa"
            -> pause

            "Pausa TIDAL"
            -> pause

            "Detén la música por ahora"
            -> pause

            ==================================================
            RESUME
            ==================================================

            Usa resume cuando el usuario quiere continuar una reproducción
            que estaba pausada.

            Ejemplos:

            "Continúa la música"
            -> resume

            "Continúa"
            -> resume

            "Sigue reproduciendo"
            -> resume

            "Reanuda la reproducción"
            -> resume

            "Dale de nuevo"
            -> resume

            ==================================================
            UNSUPPORTED
            ==================================================

            Usa unsupported cuando la solicitud no corresponde a cambiar
            el estado de reproducción multimedia.

            No clasifiques como media control:

            - cambios de volumen;
            - silenciar o quitar silencio;
            - abrir o cerrar aplicaciones;
            - preguntas sobre música;
            - búsqueda de canciones o artistas;
            - selección de una canción específica;
            - shuffle, repeat o seek, porque todavía no están soportados.

            Ejemplos:

            "Sube el volumen"
            -> unsupported

            "Silencia TIDAL"
            -> unsupported

            "Abre TIDAL"
            -> unsupported

            "Cierra Spotify"
            -> unsupported

            "¿Qué canción está sonando?"
            -> unsupported

            "Pon Comfortably Numb"
            -> unsupported

            "Busca canciones de Pink Floyd"
            -> unsupported

            ==================================================
            REGLAS
            ==================================================

            1. Clasifica la intención, no las palabras literales.

            2. Expresiones equivalentes son válidas.
               Por ejemplo "vuelve atrás" puede significar previous dentro
               del dominio de control multimedia.

            3. No confundas pausa con detener o cerrar una aplicación.
               "Pausa TIDAL" -> pause
               "Cierra TIDAL" -> unsupported

            4. No confundas reproducción con volumen.
               "Continúa la música" -> resume
               "Sube la música" si significa volumen -> unsupported

            5. Si la solicitud no expresa suficientemente una acción
               multimedia soportada, devuelve unsupported.

            6. No respondas al usuario.
               No expliques la clasificación.
               No uses Markdown.
               No agregues puntuación ni texto adicional.

            Devuelve únicamente:

            next
            previous
            pause
            resume
            unsupported
            """;
    private final OpenAIClient client;
    private final String model;
    private final MediaControlCommandDetector mediaControlDetector = new MediaControlCommandDetector();

    public LocalMediaControlParser(OpenAIClient client) {
        this(client, AppConfig.LOCAL_MODEL_ID);
    }

    public LocalMediaControlParser(OpenAIClient client, String model) {
        this.client = Objects.requireNonNull(client, "client cannot be null");
        this.model = LocalModelOutput.requireModelId(model);
    }

    @Override
    public MediaControlIntent parse(String command) {
        if (command == null || command.isBlank()) {
            return MediaControlIntent.UNSUPPORTED;
        }
        var action = mediaControlDetector.detect(command);
        if (action.isPresent()) {
            return toIntent(action.orElseThrow());
        }
        if (mediaControlDetector.definitelyNotMediaControl(command)) {
            return MediaControlIntent.UNSUPPORTED;
        }
        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model(model)
                .addSystemMessage(SYSTEM_PROMPT)
                .addUserMessage("""
                        Clasifica la siguiente solicitud multimedia.

                        <query>
                        %s
                        </query>

                        Devuelve únicamente:
                        next, previous, pause, resume o unsupported.
                        """.formatted(command.trim()))
                .temperature(0.0)
                .maxCompletionTokens(8)
                .build();
        ChatCompletion completion = client.chat().completions().create(params);
        if (completion.choices().isEmpty()) {
            return MediaControlIntent.UNSUPPORTED;
        }
        return parseClassification(LocalModelOutput.firstContractLine(completion.choices().getFirst().message().content()
                .orElse("")));
    }

    private static MediaControlIntent toIntent(MediaAction action) {
        return switch (action) {
            case NEXT -> MediaControlIntent.NEXT;
            case PREVIOUS -> MediaControlIntent.PREVIOUS;
            case PAUSE -> MediaControlIntent.PAUSE;
            case RESUME -> MediaControlIntent.RESUME;
            case PLAY_PAUSE -> throw new IllegalStateException("Detector cannot return an ambiguous toggle");
        };
    }

    static MediaControlIntent parseClassification(String classification) {
        if (classification == null || classification.isBlank()) {
            return MediaControlIntent.UNSUPPORTED;
        }
        String normalized = classification.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "next" -> MediaControlIntent.NEXT;
            case "previous" -> MediaControlIntent.PREVIOUS;
            case "pause" -> MediaControlIntent.PAUSE;
            case "resume" -> MediaControlIntent.RESUME;
            case "unsupported" -> MediaControlIntent.UNSUPPORTED;
            default -> MediaControlIntent.UNSUPPORTED;
        };
    }
}

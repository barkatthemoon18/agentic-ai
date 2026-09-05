package com.fuad.assistant.skills.audio;

import com.fuad.audio.AudioControlIntent;
import com.fuad.enums.AudioAction;
import com.fuad.enums.AudioScope;
import com.openai.client.OpenAIClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;

import java.util.Locale;
import java.util.Objects;

public class GraniteAudioControlParser implements AudioControlParser {
    private static final String MODEL = "granite-router";
    private static final String SYSTEM_PROMPT = """
        Eres un parser restringido de controles de audio para Ares.
        El usuario habla español.

        Devuelve exactamente tres campos separados por |:

        action|scope|value

        action debe ser exactamente uno de:
        set_volume, increase_volume, decrease_volume, mute, unmute, unsupported

        scope debe ser exactamente uno de:
        assistant, system, application, unknown

        value debe ser:
        - un entero entre 0 y 100 para set_volume;
        - un entero entre 1 y 100 o default para increase_volume y decrease_volume;
        - none para mute, unmute y unsupported.

        REGLAS DE ACCIÓN:

        - "al", "a" o "hasta" un porcentaje indica un nivel absoluto:
          "Sube tu volumen al 40%" -> set_volume|assistant|40
          "Pon tu voz a 25" -> set_volume|assistant|25

        - "en", "por" o "X puntos" indica una variación relativa:
          "Sube tu volumen en 15%" -> increase_volume|assistant|15
          "Baja tu voz por 20 puntos" -> decrease_volume|assistant|20

        - Un número sin preposición junto a subir o bajar es una variación:
          "Sube tu volumen 15%" -> increase_volume|assistant|15

        - Sin cantidad explícita, usa default:
          "Habla más fuerte" -> increase_volume|assistant|default
          "Baja un poco tu voz" -> decrease_volume|assistant|default

        REGLAS DE SCOPE:

        - La voz o el volumen de Ares es assistant.
        - Si no se menciona un objetivo, usa assistant.
        - Windows, el sistema, el equipo o el volumen global es system.
        - Spotify, TIDAL, VLC y cualquier aplicación concreta es application.

        EJEMPLOS:

        "Pon tu volumen al 40%" -> set_volume|assistant|40
        "Sube el volumen" -> increase_volume|assistant|default
        "Silencia tu voz" -> mute|assistant|none
        "Vuelve a hablar" -> unmute|assistant|none
        "Baja Windows al 30%" -> set_volume|system|30
        "Silencia Spotify" -> mute|application|none
        "¿Por qué Spotify no tiene sonido?" -> unsupported|application|none
        "¿Cómo funciona el volumen?" -> unsupported|unknown|none
        "Habla más lento" -> unsupported|assistant|none

        No expliques.
        No uses Markdown.
        No añadas espacios ni texto adicional.
        """;

    private final OpenAIClient client;

    public GraniteAudioControlParser(OpenAIClient client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    @Override
    public AudioControlIntent parse(String command) {
        if (command == null || command.isBlank()) {
            return AudioControlIntent.unsupported(AudioScope.UNKNOWN);
        }
        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model(MODEL)
                .addSystemMessage(SYSTEM_PROMPT)
                .addUserMessage(command)
                .temperature(0.0)
                .maxCompletionTokens(16)
                .build();
        ChatCompletion completion = client.chat().completions().create(params);
        String result = completion.choices().getFirst().message().content().orElse("");
        return parseClassification(result);
    }

    static AudioControlIntent parseClassification(String classification) {
        if (classification == null) {
            return AudioControlIntent.unsupported(AudioScope.UNKNOWN);
        }
        String[] fields = classification.trim().toLowerCase(Locale.ROOT).split("\\|", -1);
        if (fields.length != 3) {
            return AudioControlIntent.unsupported(AudioScope.UNKNOWN);
        }
        try {
            AudioAction action = parseAction(fields[0]);
            AudioScope scope = parseScope(fields[1]);
            Integer value = parseValue(action, fields[2]);
            return new AudioControlIntent(action, scope, value);
        }
        catch (IllegalArgumentException exception) {
            return AudioControlIntent.unsupported(AudioScope.UNKNOWN);
        }
    }

    private static AudioAction parseAction(String value) {
        return switch (value) {
            case "set_volume" -> AudioAction.SET_VOLUME;
            case "increase_volume" -> AudioAction.INCREASE_VOLUME;
            case "decrease_volume" -> AudioAction.DECREASE_VOLUME;
            case "mute" -> AudioAction.MUTE;
            case "unmute" -> AudioAction.UNMUTE;
            case "unsupported" -> AudioAction.UNSUPPORTED;
            default -> throw new IllegalArgumentException("Unknown audio action");
        };
    }

    private static AudioScope parseScope(String value) {
        return switch (value) {
            case "assistant" -> AudioScope.ASSISTANT;
            case "system" -> AudioScope.SYSTEM;
            case "application" -> AudioScope.APPLICATION;
            case "unknown" -> AudioScope.UNKNOWN;
            default -> throw new IllegalArgumentException("Unknown audio scope");
        };
    }

    private static Integer parseValue(AudioAction action, String value) {
        if (action == AudioAction.INCREASE_VOLUME || action == AudioAction.DECREASE_VOLUME) {
            if (value.equals("default")) {
                return null;
            }
            return Integer.valueOf(value);
        }
        if (action == AudioAction.SET_VOLUME) {
            return Integer.valueOf(value);
        }
        if (!value.equals("none")) {
            throw new IllegalArgumentException("Unexpected audio value");
        }
        return null;
    }
}

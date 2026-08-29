package com.fuad.assistant.skills.os;

import com.fuad.enums.OsAction;
import com.openai.client.OpenAIClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;

import java.util.Locale;

public class GraniteOsCommandParser implements OsCommandParser {
    private static final String MODEL = "granite-router";
    private static final String SYSTEM_PROMPT = """
        You are a restricted OS command intent parser.
        The user speaks Spanish.

        Determine whether the user is giving a DIRECT COMMAND
        to open or close Spotify NOW.

        Return exactly ONE of:

        open_application|spotify
        close_application|spotify
        unsupported|unknown

        CRITICAL RULES:

        Classify OPEN_APPLICATION or CLOSE_APPLICATION ONLY when the
        utterance is a direct request or instruction for the assistant
        to perform the action now.

        Do NOT classify descriptions, observations, future plans,
        past events, predictions or statements as commands.

        The presence of words such as "abrir", "cerrar" or "Spotify"
        is NOT sufficient by itself.

        OPEN_APPLICATION:

        "Abre Spotify" -> open_application|spotify
        "Abrir Spotify" -> open_application|spotify
        "Inicia Spotify" -> open_application|spotify
        "Pon Spotify" -> open_application|spotify
        "¿Puedes abrir Spotify?" -> open_application|spotify
        "Quiero que abras Spotify" -> open_application|spotify
        "Have de spotify" -> open_application|spotify

        CLOSE_APPLICATION:

        "Cierra Spotify" -> close_application|spotify
        "Cerrar Spotify" -> close_application|spotify
        "Termina Spotify" -> close_application|spotify
        "¿Puedes cerrar Spotify?" -> close_application|spotify
        "Quiero que cierres Spotify" -> close_application|spotify

        UNSUPPORTED:

        "Spotify se está cerrando solo" -> unsupported|unknown
        "Spotify se cerró solo" -> unsupported|unknown
        "Spotify está cerrado" -> unsupported|unknown

        "Mañana voy a cerrar Spotify" -> unsupported|unknown
        "Después voy a cerrar Spotify" -> unsupported|unknown
        "Más tarde cerraré Spotify" -> unsupported|unknown
        "Creo que voy a cerrar Spotify" -> unsupported|unknown

        "Mañana voy a abrir Spotify" -> unsupported|unknown
        "Spotify se abre solo" -> unsupported|unknown
        "Ayer abrí Spotify" -> unsupported|unknown

        "Qué es Spotify" -> unsupported|unknown
        "Spotify funciona mal" -> unsupported|unknown

        IMPORTANT:
        Gerund constructions describing what Spotify is doing,
        such as "se está cerrando", are observations, NOT commands.

        Future expressions such as "mañana", "después", "más tarde",
        "voy a abrir" or "voy a cerrar" are plans, NOT commands.

        Do not generate shell commands.
        Do not explain.
        Return only one allowed value.
        """;
    private final OpenAIClient client;

    public GraniteOsCommandParser(OpenAIClient client) {
        this.client = client;
    }

    @Override
    public OsCommandIntent parse(String command) {
        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model(MODEL)
                .addSystemMessage(SYSTEM_PROMPT)
                .addUserMessage(command)
                .temperature(0.0)
                .maxCompletionTokens(8)
                .build();
        ChatCompletion completion = client.chat().completions().create(params);
        String result = completion.choices().getFirst().message().content().orElseThrow(() ->
                new IllegalStateException("Granite returned no OS command classification")).trim().toLowerCase(Locale.ROOT);
        return switch (result) {
            case "open_application|spotify" -> new OsCommandIntent(OsAction.OPEN_APPLICATION, "spotify");
            case "close_application|spotify" -> new OsCommandIntent(OsAction.CLOSE_APPLICATION, "spotify");
            case "unsupported|unknown" -> OsCommandIntent.unsupported();
            default -> throw new IllegalStateException("Unknown OS command classification: " + result);
        };
    }
}

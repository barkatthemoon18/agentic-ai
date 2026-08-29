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

            Determine whether the utterance asks to open Spotify.

            Return exactly ONE of:

            open_application|spotify
            unsupported|unknown

            Examples:

            "Abre Spotify" -> open_application|spotify
            "Abrir Spotify" -> open_application|spotify
            "Inicia Spotify" -> open_application|spotify
            "Pon Spotify" -> open_application|spotify
            "Have de spotify" -> open_application|spotify

            "Spotify se está cerrando solo" -> unsupported|unknown
            "Mañana voy a abrir Spotify" -> unsupported|unknown
            "Qué es Spotify" -> unsupported|unknown
            "Cierra Spotify" -> unsupported|unknown

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
            case "unsupported|unknown" -> OsCommandIntent.unsupported();
            default -> throw new IllegalStateException("Unknown OS command classification: " + result);
        };
    }
}

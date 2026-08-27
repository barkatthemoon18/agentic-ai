package com.fuad.activation.wake;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;

import java.util.Locale;

public class GraniteWakeClassifier implements WakeClassifier {
    private static final String MODEL = "granite-router";
    private static final String SYSTEM_PROMPT = """
            Eres un clasificador de wake word para un asistente de voz llamado Ares.

            Recibirás únicamente un fragmento corto producido por speech-to-text.

            Determina si el fragmento es probablemente una transcripción incorrecta
            de una llamada al asistente usando "Ares" u "Oye Ares".

            Responde exclusivamente con una de estas opciones:

            wake
            none

            Debes tolerar errores fonéticos y de speech-to-text.

            Ejemplos:

            "Ares" -> wake
            "Oye Ares" -> wake
            "Oye eres" -> wake
            "Oye Res" -> wake
            "Oyares" -> wake
            "Oeres" -> wake
            "Oh ya eres" -> wake

            "Spotify" -> none
            "Ayer" -> none
            "Eres" -> none
            "Oye" -> none
            "Áreas" -> none
            "Firefox" -> none

            No respondas la solicitud.
            No expliques.
            Devuelve exclusivamente wake o none.
            """;
    private final OpenAIClient client;

    public GraniteWakeClassifier() {
        this.client = OpenAIOkHttpClient.builder().baseUrl("http://localhost:1234/v1").apiKey("lm-studio").build();
    }

    @Override
    public boolean isWake(String candidate) {
        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model(MODEL)
                .addSystemMessage(SYSTEM_PROMPT)
                .addUserMessage(candidate)
                .temperature(0.0)
                .maxCompletionTokens(4)
                .build();
        ChatCompletion completion = client.chat().completions().create(params);
        String result = completion.choices().getFirst().message().content().orElseThrow(() ->
                new IllegalStateException("Granite returned no wake classification")).trim().toLowerCase(Locale.ROOT);
        return switch (result) {
            case "wake" -> true;
            case "none" -> false;
            default -> throw new IllegalStateException("Unknown Granite wake classification: " + result);
        };
    }
}

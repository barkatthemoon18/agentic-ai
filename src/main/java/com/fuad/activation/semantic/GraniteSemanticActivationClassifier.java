package com.fuad.activation.semantic;

import com.openai.client.OpenAIClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;

import java.util.Locale;

public class GraniteSemanticActivationClassifier implements SemanticActivationClassifier {

    private static final String MODEL = "granite-router";
    private static final String SYSTEM_PROMPT = """
        You are a Spanish utterance classifier.

        Determine whether the utterance ITSELF is a direct request,
        question, command, recommendation request, or request for help
        directed to the listener.

        Return exactly:

        request
        other

        REQUEST examples:

        "Abre Spotify" -> request
        "¿Qué hora es?" -> request
        "¿Me puedes sugerir alguna canción?" -> request
        "¿Puedes recomendarme una película?" -> request
        "¿Me ayudas a entender RSA?" -> request
        "¿Qué canción me recomiendas?" -> request
        "¿Sabes qué hora es?" -> request
        "Averigua qué pasó con NVIDIA" -> request

        OTHER examples:

        "Spotify se está cerrando solo" -> other
        "Ayer fui a Spotify" -> other
        "Mañana voy a abrir Spotify" -> other
        "Creo que Firefox está actualizado" -> other
        "Juan puede abrir Spotify" -> other
        "Puedes venir mañana si quieres" -> other
        "Creo que puedes hacerlo" -> other
        "Me dijeron que puedes abrir Spotify" -> other
        "Le pregunté si podía abrir Spotify" -> other

        IMPORTANT:
        Reported speech is OTHER even when the quoted/reported content
        contains a command or request.

        Polite forms such as "¿me puedes...?", "¿podrías...?",
        "¿me ayudas...?" are REQUEST.

        Return only request or other.
        """;
    private final OpenAIClient client;

    public GraniteSemanticActivationClassifier(OpenAIClient client) {
        this.client = client;
    }

    @Override
    public boolean shouldActivate(String text) {
        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model(MODEL)
                .addSystemMessage(SYSTEM_PROMPT)
                .addUserMessage(text)
                .temperature(0.0)
                .maxCompletionTokens(4)
                .build();
        ChatCompletion completion = client.chat().completions().create(params);
        String result = completion.choices().getFirst().message().content().orElseThrow(() ->
                new IllegalStateException("Granite returned no activation classification")).trim().toLowerCase(Locale.ROOT);
        return switch (result) {
            case "request" -> true;
            case "other" -> false;
            default -> throw new IllegalStateException("Unknown activation classification: " + result);
        };
    }
}

package com.fuad.activation.semantic;

import com.openai.client.OpenAIClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;

import java.util.Locale;

public class GraniteSemanticActivationClassifier implements SemanticActivationClassifier {

    private static final String MODEL = "granite-router";
    private static final String SYSTEM_PROMPT = """
        You are a Spanish utterance classifier for a voice assistant.

        Classify ONLY the current utterance.

        Return exactly ONE label:

        request
        other

        ==================================================
        REQUEST
        ==================================================

        Use request when the current utterance independently asks
        the listener for an answer, information, explanation, help,
        recommendation, or action.

        The utterance must be understandable by itself without needing
        information from a previous conversational turn.

        Examples:

        "Abre Spotify" -> request
        "Cierra Spotify" -> request
        "¿Puedes abrir Spotify?" -> request

        "¿Qué hora es?" -> request
        "¿Quién fue Alan Turing?" -> request
        "¿Cuándo murió Alan Turing?" -> request
        "¿Dónde nació Alan Turing?" -> request
        "¿Cómo funciona RSA?" -> request
        "¿Por qué el cielo es azul?" -> request
        "¿Por qué Spotify consume tanta memoria?" -> request
        "¿Por qué RSA necesita una clave privada?" -> request
        "¿Cuál es la capital de Japón?" -> request

        "Explícame RSA" -> request
        "Ayúdame con este problema" -> request
        "Recomiéndame una película" -> request
        "Dame un ejemplo de RSA" -> request

        "¿Me puedes sugerir alguna canción?" -> request
        "¿Me ayudas a entender RSA?" -> request
        "¿Sabes qué hora es?" -> request


        ==================================================
        OTHER
        ==================================================

        Use other when the utterance does NOT independently request
        an answer or action.

        This includes:

        - statements;
        - observations;
        - descriptions;
        - past events;
        - future plans;
        - reported speech;
        - embedded questions;
        - utterances that require previous conversational context.

        Examples:

        "Spotify se está cerrando solo" -> other
        "Spotify está cerrado" -> other
        "Ayer abrí Spotify" -> other
        "Mañana voy a abrir Spotify" -> other
        "Creo que Firefox está actualizado" -> other

        "Juan puede abrir Spotify" -> other
        "Me dijeron que puedes abrir Spotify" -> other
        "Le pregunté si podía abrir Spotify" -> other

        "Me preguntaron quién fue Alan Turing" -> other
        "Juan preguntó cuándo murió Turing" -> other
        "Le expliqué cómo funciona RSA" -> other

        "No sé por qué el cielo es azul" -> other
        "Me explicó por qué funciona RSA" -> other


        ==================================================
        CONTEXT-DEPENDENT UTTERANCES
        ==================================================

        An utterance is other when it requires information from a
        previous turn to know what the speaker refers to.

        Unresolved references such as:

        - eso
        - esto
        - aquello
        - él
        - ella
        - lo anterior
        - lo mismo

        often indicate conversational dependency.

        Examples:

        "¿Por qué pasó eso?" -> other
        "¿Por qué necesita eso?" -> other
        "¿Y por qué?" -> other
        "¿Y cuándo ocurrió?" -> other
        "Explícame eso mejor" -> other
        "Dame otro ejemplo" -> other

        Contrast:

        "¿Por qué RSA necesita una clave privada?" -> request
        "¿Por qué necesita eso?" -> other

        "¿Cuándo murió Alan Turing?" -> request
        "¿Y cuándo murió?" -> other

        "Explícame RSA" -> request
        "Explícame eso mejor" -> other

        "Dame un ejemplo de RSA" -> request
        "Dame otro ejemplo" -> other


        ==================================================
        IMPORTANT RULES
        ==================================================

        Do not classify an utterance as request only because it contains
        a question word.

        Direct questions are request only when they can be understood
        independently.

        Reported or embedded questions are other.

        Judge only the CURRENT utterance.
        Do not assume previous conversational context.

        If the utterance can be answered or acted upon independently,
        return request.

        If it requires unresolved conversational context, or is merely
        a statement or report, return other.

        Return only:

        request
        other
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

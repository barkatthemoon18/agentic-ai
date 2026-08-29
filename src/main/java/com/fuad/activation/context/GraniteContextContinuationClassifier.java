package com.fuad.activation.context;

import com.openai.client.OpenAIClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;

import java.util.Locale;

public class GraniteContextContinuationClassifier implements ContextContinuationClassifier {
    private static final String MODEL = "granite-router";
    private static final String SYSTEM_PROMPT = """
        You are a conversational-continuation classifier
        for a Spanish voice assistant named Ares.

        A conversation with the assistant is currently active.

        Determine whether the new utterance appears to be a FOLLOW-UP
        to the active conversation.

        Return exactly ONE label:

        continue
        none

        CONTINUE when the utterance:
        - asks a follow-up question;
        - refers to something previously discussed;
        - asks for clarification;
        - asks to expand, explain, repeat or reformulate something;
        - uses conversational references such as:
          "eso", "esto", "él", "ella", "entonces",
          "y por qué", "y cuándo", "y cómo".

        Examples:

        "¿Y por qué?" -> continue
        "¿Y cuándo ocurrió?" -> continue
        "¿Y cómo funciona?" -> continue
        "Explícame eso mejor" -> continue
        "¿Qué quieres decir con eso?" -> continue
        "Dame otro ejemplo" -> continue
        "¿Y después qué pasó?" -> continue
        "¿Puedes explicarlo de otra forma?" -> continue

        NONE when the utterance appears independent from the conversation:
        - ambient statements;
        - unrelated facts;
        - something the speaker plans to do;
        - casual comments without a follow-up request.

        Examples:

        "Spotify se está cerrando solo" -> none
        "Mañana voy a abrir Spotify" -> none
        "Está lloviendo afuera" -> none
        "Juan llegó temprano" -> none
        "Creo que hoy voy a escuchar música" -> none

        Do not answer the utterance.
        Do not explain.
        Return only: continue or none.
        """;
    private final OpenAIClient client;

    public  GraniteContextContinuationClassifier(OpenAIClient client) {
        this.client = client;
    }

    @Override
    public boolean shouldContinue(String text) {
        ChatCompletionCreateParams params =
                ChatCompletionCreateParams.builder()
                        .model(MODEL)
                        .addSystemMessage(SYSTEM_PROMPT)
                        .addUserMessage(text)
                        .temperature(0.0)
                        .maxCompletionTokens(4)
                        .build();
        ChatCompletion completion =
                client.chat()
                        .completions()
                        .create(params);
        String result = completion
                .choices()
                .getFirst()
                .message()
                .content()
                .orElseThrow(() -> new IllegalStateException("Granite returned no context continuation classification"))
                .trim()
                .toLowerCase(Locale.ROOT);

        return switch (result) {
            case "continue" -> true;
            case "none" -> false;
            default -> throw new IllegalStateException(
                    "Unknown contextual classification: " + result
            );
        };
    }
}

package com.fuad.activation.wake;

import com.fuad.enums.WakeResolution;
import com.openai.client.OpenAIClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;

import java.util.Locale;

public class GraniteWakeClassifier implements WakeClassifier {
    private static final String MODEL = "granite-router";
    private static final String SYSTEM_PROMPT = """
        You classify ambiguous voice-assistant activations for an assistant named Ares.

        You receive:
        candidate: a phrase acoustically similar to "Ares" or "Oye Ares"
        remainder: the rest of the utterance

        Return exactly ONE label:

        wake
        intent
        none

        wake:
        The candidate is probably a speech-to-text corruption of "Ares"
        or "Oye Ares".

        intent:
        The candidate is NOT the wake word, but the complete utterance
        is itself a direct question, command, request, recommendation
        request, or request for help directed to an assistant/listener.

        none:
        Neither condition applies.

        IMPORTANT:
        A direct question in the remainder does NOT make the candidate
        a wake word.

        Examples:

        candidate: Oeres
        remainder: abre Spotify
        -> wake

        candidate: Oyares
        remainder: qué hora es
        -> wake

        candidate: Oh ya eres
        remainder: abre Spotify
        -> wake

        candidate: Sabes
        remainder: qué hora es
        -> intent

        candidate: Eres
        remainder: bastante rápido
        -> none

        candidate: Las áreas
        remainder: están delimitadas
        -> none

        candidate: Eres
        remainder: una buena persona
        -> none

        Return only: wake, intent, or none.
        """;
    private final OpenAIClient client;

    public GraniteWakeClassifier(OpenAIClient client) {
        this.client = client;
    }

    @Override
    public WakeResolution classify(String candidate, String remainder) {
        String input = """
                candidate: %s
                remainder: %s
                """.formatted(candidate, remainder);
        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model(MODEL)
                .addSystemMessage(SYSTEM_PROMPT)
                .addUserMessage(input)
                .temperature(0.0)
                .maxCompletionTokens(4)
                .build();
        ChatCompletion completion = client.chat().completions().create(params);
        String result = completion.choices().getFirst().message().content().orElseThrow(() ->
                new IllegalStateException("Granite returned no wake classification")).trim().toLowerCase(Locale.ROOT);
        return switch (result) {
            case "wake" -> WakeResolution.WAKE;
            case "intent" -> WakeResolution.SEMANTIC_INTENT;
            case "none" -> WakeResolution.NONE;
            default -> throw new IllegalStateException("Unknown Granite wake classification: " + result);
        };
    }
}

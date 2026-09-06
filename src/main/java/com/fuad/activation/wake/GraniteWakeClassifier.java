package com.fuad.activation.wake;

import com.fuad.config.AppConfig;
import com.fuad.enums.WakeResolution;
import com.fuad.model.LocalModelOutput;
import com.openai.client.OpenAIClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;

import java.util.Objects;
import java.util.Set;

public class GraniteWakeClassifier implements WakeClassifier {
    private static final Set<String> LABELS = Set.of("wake", "intent", "none");
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
    private final String model;

    public GraniteWakeClassifier(OpenAIClient client) {
        this(client, AppConfig.LOCAL_MODEL_ID);
    }

    public GraniteWakeClassifier(OpenAIClient client, String model) {
        this.client = Objects.requireNonNull(client, "client cannot be null");
        this.model = LocalModelOutput.requireModelId(model);
    }

    @Override
    public WakeResolution classify(String candidate, String remainder) {
        String input = """
                candidate: %s
                remainder: %s
                """.formatted(candidate, remainder);
        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model(model)
                .addSystemMessage(SYSTEM_PROMPT)
                .addUserMessage(input)
                .temperature(0.0)
                .maxCompletionTokens(4)
                .build();
        ChatCompletion completion = client.chat().completions().create(params);
        String output = completion.choices().getFirst().message().content().orElseThrow(() ->
                new IllegalStateException("Local model returned no wake classification"));
        String result = LocalModelOutput.extractLeadingLabel(output, LABELS, "wake classification");
        return switch (result) {
            case "wake" -> WakeResolution.WAKE;
            case "intent" -> WakeResolution.SEMANTIC_INTENT;
            case "none" -> WakeResolution.NONE;
            default -> throw new IllegalStateException("Unknown wake classification: " + result);
        };
    }
}

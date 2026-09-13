package com.fuad.assistant.skills.os;

import com.openai.client.OpenAIClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;

import java.util.Objects;
import java.util.Optional;

final class OpenAiOsCommandInference implements OsCommandInference {
    private static final String RETRY_INSTRUCTION = """
            Tu salida anterior incumplió el contrato. Devuelve exactamente una sola línea válida
            con action|application, sin explicación, Markdown ni texto adicional.
            """;

    private final OpenAIClient client;
    private final String model;

    OpenAiOsCommandInference(OpenAIClient client, String model) {
        this.client = Objects.requireNonNull(client, "client cannot be null");
        this.model = Objects.requireNonNull(model, "model cannot be null");
    }

    @Override
    public Optional<String> infer(OsCommandInferenceRequest request) {
        Objects.requireNonNull(request, "request cannot be null");
        ChatCompletionCreateParams.Builder params = ChatCompletionCreateParams.builder()
                .model(model)
                .addSystemMessage(request.instructions())
                .addUserMessage("""
                        <command>
                        %s
                        </command>
                        """.formatted(request.command()))
                .temperature(0.0)
                .maxCompletionTokens(request.maxCompletionTokens());
        if (request.retry()) {
            if (request.previousInvalidOutput() != null && !request.previousInvalidOutput().isBlank()) {
                params.addAssistantMessage(request.previousInvalidOutput());
            }
            params.addUserMessage(RETRY_INSTRUCTION);
        }
        ChatCompletion completion = client.chat().completions().create(params.build());
        return completion.choices().getFirst().message().content();
    }
}

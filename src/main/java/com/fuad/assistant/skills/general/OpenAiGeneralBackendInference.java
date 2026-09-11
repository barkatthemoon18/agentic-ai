package com.fuad.assistant.skills.general;

import com.fuad.model.LocalModelOutput;
import com.openai.client.OpenAIClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;

import java.util.Objects;
import java.util.Optional;

public final class OpenAiGeneralBackendInference implements GeneralBackendInference {
    private static final String RETRY_INSTRUCTION =
            "Tu salida anterior fue inválida. Devuelve únicamente una etiqueta: local o gpt.";

    private final OpenAIClient client;
    private final String model;

    public OpenAiGeneralBackendInference(OpenAIClient client, String model) {
        this.client = Objects.requireNonNull(client, "client cannot be null");
        this.model = LocalModelOutput.requireModelId(model);
    }

    @Override
    public Optional<String> infer(GeneralBackendInferenceRequest request) {
        Objects.requireNonNull(request, "request cannot be null");
        ChatCompletionCreateParams.Builder params = ChatCompletionCreateParams.builder()
                .model(model)
                .addSystemMessage(request.instructions())
                .addUserMessage("""
                        <query>
                        %s
                        </query>
                        """.formatted(request.query()))
                .temperature(0.0)
                .maxCompletionTokens(request.maxCompletionTokens());
        if (request.retry()) {
            if (request.previousInvalidOutput() != null
                    && !request.previousInvalidOutput().isBlank()) {
                params.addAssistantMessage(request.previousInvalidOutput());
            }
            params.addUserMessage(RETRY_INSTRUCTION);
        }
        ChatCompletion completion = client.chat().completions().create(params.build());
        return completion.choices().getFirst().message().content();
    }
}

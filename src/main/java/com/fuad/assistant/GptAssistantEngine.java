package com.fuad.assistant;

import com.openai.client.OpenAIClient;
import com.openai.models.ChatModel;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseOutputText;

public class GptAssistantEngine implements AssistantEngine {
    private final OpenAIClient client;

    public GptAssistantEngine(OpenAIClient client) {
        this.client = client;
    }

    @Override
    public AssistantResult process(AssistantRequest request) {
        ResponseCreateParams.Builder builder = ResponseCreateParams.builder().model(ChatModel.GPT_5_6_LUNA)
                .input(request.getCommand())
                .instructions(request.getInstructions())
                .maxOutputTokens(request.getMaxOutputTokens());
        String continuationToken = request.getContinuationToken();
        if (continuationToken != null && !continuationToken.isBlank()) {
            builder.previousResponseId(continuationToken);
        }
        Response response = client.responses().create(builder.build());
        String text = response.output()
                .stream()
                .flatMap(item -> item.message().stream())
                .flatMap(message -> message.content().stream())
                .flatMap(content -> content.outputText().stream())
                .map(ResponseOutputText::text)
                .reduce("", String::concat);
        return new AssistantResult(text, response.id());
    }
}

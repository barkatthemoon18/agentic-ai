package com.fuad.assistant;

import com.openai.client.OpenAIClient;
import com.openai.models.ChatModel;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseOutputText;

public class GptAssistantEngine implements AssistantEngine {
    private final OpenAIClient client;
    private String previousResponseId;

    public GptAssistantEngine(OpenAIClient client) {
        this.client = client;
    }

    @Override
    public AssistantResult process(AssistantRequest request) {
        ResponseCreateParams.Builder builder = ResponseCreateParams.builder().model(ChatModel.GPT_5_6_LUNA)
                .input(request.getCommand())
                .instructions(request.getInstructions())
                .maxOutputTokens(request.getMaxOutputTokens());
        if (previousResponseId != null) {
            builder.previousResponseId(previousResponseId);
        }
        Response response = client.responses().create(builder.build());
        previousResponseId = response.id();
        String text = response.output()
                .stream()
                .flatMap(item -> item.message().stream())
                .flatMap(message -> message.content().stream())
                .flatMap(content -> content.outputText().stream())
                .map(ResponseOutputText::text)
                .reduce("", String::concat);
        return new AssistantResult(text);
    }

    @Override
    public void resetConversation() {
        previousResponseId = null;
    }
}

package com.fuad.assistant;

import com.fuad.enums.ResearchDepth;
import com.openai.client.OpenAIClient;
import com.openai.models.ChatModel;
import com.openai.models.Reasoning;
import com.openai.models.ReasoningEffort;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseOutputText;
import com.openai.models.responses.ToolChoiceOptions;
import com.openai.models.responses.WebSearchTool;

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
        configureResearch(builder, request.getResearchDepth());
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
                .reduce("", String::concat)
                .trim();
        if (text.isEmpty()) {
            throw new IllegalStateException("OpenAI returned no assistant text");
        }
        return new AssistantResult(text, response.id());
    }

    private void configureResearch(ResponseCreateParams.Builder builder, ResearchDepth depth) {
        if (depth == ResearchDepth.NONE) {
            return;
        }

        boolean deep = depth == ResearchDepth.DEEP;
        builder.addTool(WebSearchTool.builder()
                        .type(WebSearchTool.Type.WEB_SEARCH)
                        .searchContextSize(deep
                                ? WebSearchTool.SearchContextSize.HIGH
                                : WebSearchTool.SearchContextSize.LOW)
                        .build())
                .toolChoice(ToolChoiceOptions.REQUIRED)
                .reasoning(Reasoning.builder()
                        .effort(deep ? ReasoningEffort.HIGH : ReasoningEffort.LOW)
                        .build())
                .maxToolCalls(deep ? 6 : 2);
    }
}

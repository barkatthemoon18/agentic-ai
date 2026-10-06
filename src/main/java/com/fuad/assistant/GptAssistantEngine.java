package com.fuad.assistant;

import com.fuad.assistant.skills.research.ResearchAccess;
import com.fuad.assistant.skills.research.ResearchDepth;
import com.openai.client.OpenAIClient;
import com.openai.models.ChatModel;
import com.openai.models.Reasoning;
import com.openai.models.ReasoningEffort;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseOutputText;
import com.openai.models.responses.ToolChoiceOptions;
import com.openai.models.responses.WebSearchTool;
import com.openai.models.responses.ResponseOutputItem;

public class GptAssistantEngine implements AssistantEngine {
    private final OpenAIClient client;

    public GptAssistantEngine(OpenAIClient client) {
        this.client = client;
    }

    @Override
    public AssistantResult process(AssistantRequest request) {
        ResponseCreateParams.Builder builder = ResponseCreateParams.builder().model(ChatModel.GPT_5_6_LUNA)
                .input(request.command())
                .instructions(request.instructions())
                .maxOutputTokens(request.maxOutputTokens());
        configureResearch(builder, request.researchDepth(), request.researchAccess());
        String continuationToken = request.continuationToken();
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
            String outputKinds = response.output().stream().map(GptAssistantEngine::outputKind).toList().toString();
            throw new IllegalStateException(
                    "OpenAI returned no assistant text"
                            + " | status=" + response.status().orElse(null)
                            + " | incomplete=" + response.incompleteDetails().orElse(null)
                            + " | error=" + response.error().orElse(null)
                            + " | usage=" + response.usage().orElse(null)
                            + " | output=" + outputKinds);
        }
        return new AssistantResult(text, response.id());
    }

    private static String outputKind(ResponseOutputItem item) {
        if (item.isMessage()) {
            return "message";
        }

        if (item.isWebSearchCall()) {
            return "web_search_call";
        }

        if (item.isReasoning()) {
            return "reasoning";
        }

        return item.toString();
    }

    private void configureResearch(ResponseCreateParams.Builder builder, ResearchDepth depth,
                                   ResearchAccess researchAccess) {
        configureReasoning(builder, depth);
        configureWebSearch(builder, researchAccess, depth);
    }

    private void configureReasoning(ResponseCreateParams.Builder builder, ResearchDepth depth) {
        if (depth == ResearchDepth.NONE) {
            return;
        }
        builder.reasoning(Reasoning.builder().effort(depth == ResearchDepth.DEEP ?
                ReasoningEffort.HIGH : ReasoningEffort.LOW).build());
    }

    private void configureWebSearch(ResponseCreateParams.Builder builder, ResearchAccess access, ResearchDepth depth) {
        if (access != ResearchAccess.WEB_REQUIRED) {
            return;
        }
        boolean deep = depth == ResearchDepth.DEEP;
        builder.addTool(WebSearchTool.builder().type(WebSearchTool.Type.WEB_SEARCH).searchContextSize(deep ?
                WebSearchTool.SearchContextSize.HIGH : WebSearchTool.SearchContextSize.LOW).build())
                .toolChoice(ToolChoiceOptions.REQUIRED).maxToolCalls(deep ? 6 : 2);
    }
}

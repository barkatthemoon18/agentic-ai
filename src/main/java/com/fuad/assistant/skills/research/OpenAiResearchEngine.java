package com.fuad.assistant.skills.research;

import com.fuad.assistant.AssistantEngine;
import com.fuad.assistant.AssistantRequest;
import com.fuad.assistant.AssistantResult;

import java.util.Objects;

public class OpenAiResearchEngine implements QuickResearchEngine {
    private final AssistantEngine assistantEngine;

    public OpenAiResearchEngine(AssistantEngine assistantEngine) {
        this.assistantEngine = Objects.requireNonNull(assistantEngine, "assistantEngine cannot be null");
    }

    @Override
    public ResearchEngineResult research(ResearchRequest request) {
        String command = request.query();
        if ((request.continuation().continuationToken() == null
                || request.continuation().continuationToken().isBlank())
                && !request.previousMessages().isEmpty()) {
            StringBuilder contextualCommand = new StringBuilder("Contexto previo de la conversacion:\n");
            for (ResearchMessage message : request.previousMessages()) {
                contextualCommand.append(message.role() == ResearchMessage.Role.USER ? "Usuario: " : "Asistente: ")
                        .append(message.content()).append('\n');
            }
            contextualCommand.append("Consulta actual: ").append(request.query());
            command = contextualCommand.toString();
        }
        AssistantResult result = assistantEngine.process(new AssistantRequest(
                command, request.instructions(), request.maxOutputTokens(),
                request.continuation().continuationToken(), request.depth(), request.access()));
        return new ResearchEngineResult(result.text(),
                new ResearchBranchState(result.continuationToken(), request.previousMessages()));
    }
}

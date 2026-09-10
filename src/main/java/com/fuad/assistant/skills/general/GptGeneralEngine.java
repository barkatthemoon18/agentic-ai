package com.fuad.assistant.skills.general;

import com.fuad.assistant.AssistantEngine;
import com.fuad.assistant.AssistantRequest;
import com.fuad.assistant.AssistantResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class GptGeneralEngine implements GeneralEngine {
    private final AssistantEngine assistantEngine;

    public GptGeneralEngine(AssistantEngine assistantEngine) {
        this.assistantEngine = Objects.requireNonNull(assistantEngine, "assistantEngine cannot be null");
    }

    @Override
    public GeneralEngineResult respond(GeneralRequest request) {
        Objects.requireNonNull(request, "request cannot be null");
        GeneralBranchState branch = request.continuation();
        String command = branch.continuationToken() == null && !branch.messages().isEmpty()
                ? contextualCommand(branch.messages(), request.command())
                : request.command();
        AssistantResult result = assistantEngine.process(new AssistantRequest(command,
                request.instructions(), request.maxOutputTokens(), branch.continuationToken()));

        List<GeneralMessage> messages = new ArrayList<>(branch.messages());
        messages.add(new GeneralMessage(GeneralMessage.Role.USER, request.command()));
        messages.add(new GeneralMessage(GeneralMessage.Role.ASSISTANT, result.getText()));
        return new GeneralEngineResult(result.getText(),
                new GeneralBranchState(result.getContinuationToken(), messages));
    }

    private String contextualCommand(List<GeneralMessage> messages, String command) {
        StringBuilder transcript = new StringBuilder("Contexto previo de la conversacion:\n");
        for (GeneralMessage message : messages) {
            transcript.append(message.role() == GeneralMessage.Role.USER ? "Usuario: " : "Asistente: ")
                    .append(message.content()).append('\n');
        }
        return transcript.append("Usuario: ").append(command).toString();
    }
}

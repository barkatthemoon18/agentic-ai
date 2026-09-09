package com.fuad.assistant.skills.research;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class QwenLocalResearchEngine implements QuickResearchEngine {
    private final LocalQwenChatClient client;

    public QwenLocalResearchEngine(LocalQwenChatClient client) {
        this.client = Objects.requireNonNull(client, "client cannot be null");
    }

    @Override
    public ResearchEngineResult research(ResearchRequest request) {
        Objects.requireNonNull(request, "request cannot be null");
        List<LocalQwenChatClient.Message> chatMessages = new ArrayList<>();
        for (ResearchMessage message : request.previousMessages()) {
            LocalQwenChatClient.Role role = message.role() == ResearchMessage.Role.USER
                    ? LocalQwenChatClient.Role.USER
                    : LocalQwenChatClient.Role.ASSISTANT;
            chatMessages.add(new LocalQwenChatClient.Message(role, message.content()));
        }
        chatMessages.add(new LocalQwenChatClient.Message(LocalQwenChatClient.Role.USER, request.query()));
        String text = client.chat(request.instructions(), chatMessages, request.maxOutputTokens());

        List<ResearchMessage> messages = new ArrayList<>(request.previousMessages());
        messages.add(new ResearchMessage(ResearchMessage.Role.USER, request.query()));
        messages.add(new ResearchMessage(ResearchMessage.Role.ASSISTANT, text));
        return new ResearchEngineResult(text, new ResearchBranchState(null, messages));
    }
}

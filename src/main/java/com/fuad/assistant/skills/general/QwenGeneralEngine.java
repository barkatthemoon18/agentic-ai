package com.fuad.assistant.skills.general;

import com.fuad.assistant.local.LocalQwenChatClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class QwenGeneralEngine implements GeneralEngine {
    private final LocalQwenChatClient client;

    public QwenGeneralEngine(LocalQwenChatClient client) {
        this.client = Objects.requireNonNull(client, "client cannot be null");
    }

    @Override
    public GeneralEngineResult respond(GeneralRequest request) {
        Objects.requireNonNull(request, "request cannot be null");
        List<LocalQwenChatClient.Message> chatMessages = new ArrayList<>();
        for (GeneralMessage message : request.continuation().messages()) {
            chatMessages.add(new LocalQwenChatClient.Message(
                    message.role() == GeneralMessage.Role.USER
                            ? LocalQwenChatClient.Role.USER
                            : LocalQwenChatClient.Role.ASSISTANT,
                    message.content()));
        }
        chatMessages.add(new LocalQwenChatClient.Message(LocalQwenChatClient.Role.USER,
                request.command()));
        String text = client.chat(request.instructions(), chatMessages, request.maxOutputTokens());

        List<GeneralMessage> messages = new ArrayList<>(request.continuation().messages());
        messages.add(new GeneralMessage(GeneralMessage.Role.USER, request.command()));
        messages.add(new GeneralMessage(GeneralMessage.Role.ASSISTANT, text));
        return new GeneralEngineResult(text, new GeneralBranchState(null, messages));
    }
}

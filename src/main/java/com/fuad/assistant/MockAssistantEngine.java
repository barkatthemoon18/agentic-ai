package com.fuad.assistant;

public class MockAssistantEngine implements AssistantEngine {
    @Override
    public AssistantResult process(AssistantRequest command) {
        return new AssistantResult("Recibí el comando: " + command);
    }

    @Override
    public void resetConversation() {

    }
}

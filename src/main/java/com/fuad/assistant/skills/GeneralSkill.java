package com.fuad.assistant.skills;

import com.fuad.assistant.AssistantEngine;
import com.fuad.assistant.AssistantRequest;
import com.fuad.assistant.AssistantResult;
import com.fuad.enums.ConversationPolicy;

public class GeneralSkill implements Skill {
    private static final String INSTRUCTIONS = "Responde de forma breve, natural y conversacional. " +
            "Normalmente responde en 1 a 3 frases. No añadas contexto, listas o antecedentes que el usuario no haya solicitado. " +
            "Si el usuario solicita explícitamente mayor detalle, adapta la extensión de la respuesta. " +
            "La respuesta será reproducida mediante voz, por lo que evita formato innecesario.";
    private final AssistantEngine assistantEngine;

    public GeneralSkill(AssistantEngine assistantEngine) {
        this.assistantEngine = assistantEngine;
    }

    @Override
    public AssistantResult execute(String command) {
        return assistantEngine.process(new AssistantRequest(command, INSTRUCTIONS, 300));
    }

    @Override
    public ConversationPolicy conversationPolicy() {
        return ConversationPolicy.KEEP_OPEN;
    }
}

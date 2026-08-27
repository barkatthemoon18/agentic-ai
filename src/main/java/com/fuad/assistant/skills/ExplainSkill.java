package com.fuad.assistant.skills;

import com.fuad.assistant.AssistantEngine;
import com.fuad.assistant.AssistantRequest;
import com.fuad.assistant.AssistantResult;
import com.fuad.enums.SkillType;

public class ExplainSkill implements Skill {
    private static final String INSTRUCTIONS = "Eres Ares, un asistente de voz. " +
            "El usuario ha solicitado una explicación desarrollada, análisis o mayor profundidad. " +
            "Explica el tema de forma clara, estructurada y natural. Incluye el contexto necesario para comprender la respuesta, " +
            "pero evita información irrelevante o repetitiva. Adapta la profundidad a lo que el usuario haya solicitado." +
            "No extiendas artificialmente la respuesta. Como la respuesta será reproducida mediante voz, " +
            "evita markdown complejo, tablas y formato visual innecesario.";
    private final AssistantEngine  assistantEngine;

    public ExplainSkill(AssistantEngine assistantEngine) {
        this.assistantEngine = assistantEngine;
    }

    @Override
    public SkillType getType() {
        return SkillType.EXPLAIN;
    }

    @Override
    public AssistantResult execute(String command) {
        return assistantEngine.process(new AssistantRequest(command, INSTRUCTIONS, 800));
    }
}

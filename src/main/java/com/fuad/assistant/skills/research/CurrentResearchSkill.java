package com.fuad.assistant.skills.research;

import com.fuad.assistant.AssistantEngine;
import com.fuad.assistant.AssistantRequest;
import com.fuad.assistant.AssistantResult;
import com.fuad.assistant.skills.Skill;
import com.fuad.enums.ConversationPolicy;
import com.fuad.enums.ResearchDepth;

import java.util.Objects;

public class CurrentResearchSkill implements Skill {
    private static final String QUICK_INSTRUCTIONS = """
            Antes de responder, busca informacion actual en Internet.
            Responde en espanol, de forma natural y adecuada para voz.
            Resume el resultado en 1 a 3 frases.
            Menciona fechas concretas cuando sean relevantes.
            Termina mencionando por nombre entre 2 y 3 fuentes consultadas.
            No leas ni incluyas URLs largas.
            Si las fuentes no permiten confirmar algo, dilo claramente.
            """;
    private static final String DEEP_INSTRUCTIONS = """
            Investiga la consulta usando informacion actual de Internet.
            Contrasta varias fuentes y prioriza fuentes primarias u oficiales.
            Responde en espanol y sintetiza los hallazgos mas relevantes
            en un maximo de 6 frases aptas para reproduccion por voz.
            Distingue hechos confirmados, desacuerdos e incertidumbre.
            Menciona fechas concretas cuando sean relevantes.
            Termina mencionando por nombre entre 2 y 3 fuentes consultadas.
            No leas ni incluyas URLs largas.
            """;

    private final AssistantEngine assistantEngine;
    private final ResearchDepthClassifier depthClassifier;

    public CurrentResearchSkill(AssistantEngine assistantEngine, ResearchDepthClassifier depthClassifier) {
        this.assistantEngine = Objects.requireNonNull(assistantEngine, "assistantEngine cannot be null");
        this.depthClassifier = Objects.requireNonNull(depthClassifier, "depthClassifier cannot be null");
    }

    @Override
    public AssistantResult execute(String command) {
        return execute(command, null);
    }

    @Override
    public AssistantResult execute(String command, String continuationToken) {
        ResearchDepth depth = depthClassifier.classify(command);
        boolean deep = depth == ResearchDepth.DEEP;
        return assistantEngine.process(new AssistantRequest(
                command,
                deep ? DEEP_INSTRUCTIONS : QUICK_INSTRUCTIONS,
                deep ? 1200 : 500,
                continuationToken,
                depth));
    }

    @Override
    public ConversationPolicy getConversationPolicy() {
        return ConversationPolicy.KEEP_OPEN;
    }
}

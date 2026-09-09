package com.fuad.assistant.skills.research;

import com.fuad.assistant.AssistantResult;
import com.fuad.assistant.session.ConversationSnapshot;
import com.fuad.assistant.skills.Skill;
import com.fuad.enums.Capability;
import com.fuad.enums.ConversationPolicy;
import com.fuad.enums.ResearchDepth;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class CurrentResearchSkill implements Skill {
    private static final String WEB_QUICK_INSTRUCTIONS = """
            Antes de responder, busca informacion actual en Internet.
            Responde en espanol, de forma natural y adecuada para voz.
            Resume el resultado en 1 a 3 frases.
            Menciona fechas concretas cuando sean relevantes.
            Termina mencionando por nombre entre 2 y 3 fuentes consultadas.
            No leas ni incluyas URLs largas.
            Si las fuentes no permiten confirmar algo, dilo claramente.
            """;
    private static final String WEB_DEEP_INSTRUCTIONS = """
            Investiga la consulta usando informacion actual de Internet.
            Contrasta varias fuentes y prioriza fuentes primarias u oficiales.
            Responde en espanol y sintetiza los hallazgos mas relevantes
            en un maximo de 6 frases aptas para reproduccion por voz.
            Distingue hechos confirmados, desacuerdos e incertidumbre.
            Menciona fechas concretas cuando sean relevantes.
            Termina mencionando por nombre entre 2 y 3 fuentes consultadas.
            No leas ni incluyas URLs largas.
            """;
    private static final String LOCAL_QUICK_INSTRUCTIONS = """
            Responde usando exclusivamente tu conocimiento interno, sin buscar en Internet.
            Responde en espanol, de forma natural y adecuada para voz, en 1 a 3 frases.
            Si la consulta requiere informacion actual que no puedes verificar, dilo claramente.
            """;
    private static final String LOCAL_DEEP_INSTRUCTIONS = """
            Analiza la consulta usando exclusivamente tu conocimiento interno, sin buscar en Internet.
            Responde en espanol, de forma natural y adecuada para voz, en un maximo de 6 frases.
            Distingue hechos, interpretaciones e incertidumbre cuando sea relevante.
            Si la consulta requiere informacion actual que no puedes verificar, dilo claramente.
            """;

    private final QuickResearchEngine globalEngine;
    private final QuickResearchEngine localEngine;
    private final ResearchDepthClassifier depthClassifier;
    private final ResearchBackendClassifier backendClassifier;

    public CurrentResearchSkill(QuickResearchEngine globalEngine, QuickResearchEngine localEngine,
                                ResearchDepthClassifier depthClassifier,
                                ResearchBackendClassifier backendClassifier) {
        this.globalEngine = Objects.requireNonNull(globalEngine, "globalEngine cannot be null");
        this.localEngine = Objects.requireNonNull(localEngine, "localEngine cannot be null");
        this.depthClassifier = Objects.requireNonNull(depthClassifier, "depthClassifier cannot be null");
        this.backendClassifier = Objects.requireNonNull(backendClassifier, "backendClassifier cannot be null");
    }

    @Override
    public AssistantResult execute(String command) {
        return executeInternal(command, null);
    }

    @Override
    public AssistantResult execute(String command, String continuationToken) {
        ResearchConversationState state = continuationToken == null || continuationToken.isBlank()
                ? null
                : new ResearchConversationState(ResearchBackend.GPT_WEB, Map.of(
                        ResearchBackend.GPT_WEB, new ResearchBranchState(continuationToken, List.of())));
        return executeInternal(command, state == null ? null : new ConversationSnapshot(
                Capability.CURRENT_RESEARCH, command, "Contexto anterior no disponible", continuationToken, state));
    }

    @Override
    public AssistantResult executeFollowUp(String command, ConversationSnapshot snapshot) {
        return executeInternal(command, snapshot);
    }

    private AssistantResult executeInternal(String command, ConversationSnapshot snapshot) {
        ResearchConversationState state = stateFrom(snapshot);
        ResearchBackend inherited = state.getActiveBackend().orElse(null);
        ResearchBackend backend = backendClassifier.classify(command, inherited);
        ResearchDepth depth = depthClassifier.classify(command);
        boolean deep = depth == ResearchDepth.DEEP;

        ResearchBranchState branch = state.getBranch(backend).orElseGet(() -> seedBranch(snapshot));
        ResearchRequest request = new ResearchRequest(command, instructions(backend, deep),
                deep ? 1200 : 500, depth, branch);
        ResearchEngineResult result = engine(backend).research(request);
        ResearchConversationState updated = state.withBranch(backend, result.continuation());
        return new AssistantResult(result.text(), result.continuation().continuationToken(), updated);
    }

    private ResearchConversationState stateFrom(ConversationSnapshot snapshot) {
        if (snapshot == null) {
            return ResearchConversationState.empty();
        }
        if (snapshot.getResearchConversationState() != null) {
            return snapshot.getResearchConversationState();
        }
        String token = snapshot.getContinuationToken();
        if (token != null && !token.isBlank()) {
            return new ResearchConversationState(ResearchBackend.GPT_WEB, Map.of(
                    ResearchBackend.GPT_WEB, new ResearchBranchState(token, List.of())));
        }
        return ResearchConversationState.empty();
    }

    private ResearchBranchState seedBranch(ConversationSnapshot snapshot) {
        if (snapshot == null) {
            return ResearchBranchState.empty();
        }
        List<ResearchMessage> messages = new ArrayList<>();
        messages.add(new ResearchMessage(ResearchMessage.Role.USER, snapshot.getPreviousUserText()));
        messages.add(new ResearchMessage(ResearchMessage.Role.ASSISTANT, snapshot.getPreviousAssistantText()));
        return new ResearchBranchState(null, messages);
    }

    private QuickResearchEngine engine(ResearchBackend backend) {
        return backend == ResearchBackend.GPT_WEB ? globalEngine : localEngine;
    }

    private String instructions(ResearchBackend backend, boolean deep) {
        if (backend == ResearchBackend.GPT_WEB) {
            return deep ? WEB_DEEP_INSTRUCTIONS : WEB_QUICK_INSTRUCTIONS;
        }
        return deep ? LOCAL_DEEP_INSTRUCTIONS : LOCAL_QUICK_INSTRUCTIONS;
    }

    @Override
    public ConversationPolicy getConversationPolicy() {
        return ConversationPolicy.KEEP_OPEN;
    }
}

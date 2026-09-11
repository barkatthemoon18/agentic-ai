package com.fuad.assistant.skills.research;

import com.fuad.assistant.AssistantResult;
import com.fuad.assistant.session.ConversationSnapshot;
import com.fuad.enums.Capability;
import com.fuad.enums.ResearchDepth;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResearchBackendRoutingTest {
    private final DefaultResearchBackendClassifier classifier = new DefaultResearchBackendClassifier();

    @Test
    void classifierShouldHonorScopeAndRouteFreshInformationToWeb() {
        assertEquals(ResearchBackend.GPT_WEB,
                classifier.classify("Busca globalmente quien fue Alan Turing", null));
        assertEquals(ResearchBackend.QWEN_LOCAL,
                classifier.classify("Busca localmente quien fue Alan Turing", ResearchBackend.GPT_WEB));
        assertEquals(ResearchBackend.GPT_WEB,
                classifier.classify("Que ocurrio hoy con NVIDIA", ResearchBackend.QWEN_LOCAL));
        assertEquals(ResearchBackend.QWEN_LOCAL,
                classifier.classify("Busca informacion sobre Alan Turing", null));
        assertEquals(ResearchBackend.QWEN_LOCAL,
                classifier.classify("Explicame mas", ResearchBackend.QWEN_LOCAL));
        assertEquals(ResearchBackend.GPT_WEB,
                classifier.classify("Dame las fuentes que encontraste", ResearchBackend.QWEN_LOCAL));
        assertEquals(ResearchBackend.GPT_WEB,
                classifier.classify("Verifica si eso sigue siendo cierto", ResearchBackend.QWEN_LOCAL));
        assertEquals(ResearchBackend.GPT_WEB,
                classifier.classify("Dame las fuentes", ResearchBackend.QWEN_LOCAL));
        assertEquals(ResearchBackend.GPT_WEB,
                classifier.classify("Verifica si sigue siendo cierto", ResearchBackend.QWEN_LOCAL));
    }

    @Test
    void nowAloneShouldInheritTheResearchBackendOrDefaultToLocal() {
        assertEquals(ResearchBackend.QWEN_LOCAL,
                classifier.classify("Ahora profundiza", ResearchBackend.QWEN_LOCAL));
        assertEquals(ResearchBackend.GPT_WEB,
                classifier.classify("Ahora profundiza", ResearchBackend.GPT_WEB));
        assertEquals(ResearchBackend.QWEN_LOCAL,
                classifier.classify("Ahora profundiza", null));
    }

    @Test
    void switchingBackendsShouldPreserveIndependentBranches() {
        AtomicReference<ResearchRequest> globalRequest = new AtomicReference<>();
        AtomicReference<ResearchRequest> localRequest = new AtomicReference<>();
        QuickResearchEngine global = request -> {
            globalRequest.set(request);
            return new ResearchEngineResult("respuesta global",
                    new ResearchBranchState("gpt-1", request.previousMessages()));
        };
        QuickResearchEngine local = request -> {
            localRequest.set(request);
            List<ResearchMessage> messages = new ArrayList<>(request.previousMessages());
            messages.add(new ResearchMessage(ResearchMessage.Role.USER, request.query()));
            messages.add(new ResearchMessage(ResearchMessage.Role.ASSISTANT, "respuesta local"));
            return new ResearchEngineResult("respuesta local", new ResearchBranchState(null, messages));
        };
        CurrentResearchSkill skill = new CurrentResearchSkill(
                global, local, ignored -> ResearchDepth.QUICK, classifier);

        AssistantResult first = skill.execute("Busca globalmente quien fue Alan Turing");
        AssistantResult second = skill.executeFollowUp("Ahora buscalo localmente",
                snapshot("Busca globalmente quien fue Alan Turing", first));

        assertEquals(ResearchBackend.QWEN_LOCAL,
                second.getResearchConversationState().getActiveBackend().orElseThrow());
        assertEquals("gpt-1", second.getResearchConversationState()
                .getBranch(ResearchBackend.GPT_WEB).orElseThrow().continuationToken());
        assertEquals(2, localRequest.get().previousMessages().size());
        assertEquals("respuesta global", localRequest.get().previousMessages().get(1).content());

        AssistantResult third = skill.executeFollowUp("Explicame mas",
                snapshot("Ahora buscalo localmente", second));
        assertEquals(4, localRequest.get().previousMessages().size());
        assertEquals(ResearchBackend.QWEN_LOCAL,
                third.getResearchConversationState().getActiveBackend().orElseThrow());

        skill.executeFollowUp("Vuelve a buscarlo globalmente", snapshot("Explicame mas", third));
        assertEquals("gpt-1", globalRequest.get().continuation().continuationToken());
        assertFalse(globalRequest.get().instructions().contains("exclusivamente tu conocimiento interno"));
        assertTrue(localRequest.get().instructions().contains("sin buscar en Internet"));
    }

    private ConversationSnapshot snapshot(String userText, AssistantResult result) {
        return new ConversationSnapshot(Capability.CURRENT_RESEARCH, userText, result.getText(),
                result.getContinuationToken(), result.getResearchConversationState());
    }
}

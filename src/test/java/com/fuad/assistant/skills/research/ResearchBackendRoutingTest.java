package com.fuad.assistant.skills.research;

import com.fuad.assistant.AssistantResult;
import com.fuad.assistant.session.ConversationSnapshot;
import com.fuad.enums.Capability;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResearchBackendRoutingTest {
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
        ArrayDeque<ResearchPlan> plans = new ArrayDeque<>(List.of(
                new ResearchPlan(ResearchAccess.WEB_REQUIRED, ResearchDepth.QUICK),
                new ResearchPlan(ResearchAccess.MODEL_KNOWLEDGE, ResearchDepth.QUICK),
                new ResearchPlan(ResearchAccess.MODEL_KNOWLEDGE, ResearchDepth.QUICK),
                new ResearchPlan(ResearchAccess.WEB_REQUIRED, ResearchDepth.QUICK)));
        CurrentResearchSkill skill = new CurrentResearchSkill(
                global, local, ignored -> plans.removeFirst());

        AssistantResult first = skill.execute("Busca globalmente quien fue Alan Turing");
        AssistantResult second = skill.executeFollowUp("Ahora buscalo localmente",
                snapshot("Busca globalmente quien fue Alan Turing", first));

        assertEquals(ResearchBackend.QWEN_LOCAL,
                second.researchConversationState().getActiveBackend().orElseThrow());
        assertEquals("gpt-1", second.researchConversationState()
                .getBranch(ResearchBackend.GPT_API).orElseThrow().continuationToken());
        assertEquals(2, localRequest.get().previousMessages().size());
        assertEquals("respuesta global", localRequest.get().previousMessages().get(1).content());

        AssistantResult third = skill.executeFollowUp("Explicame mas",
                snapshot("Ahora buscalo localmente", second));
        assertEquals(4, localRequest.get().previousMessages().size());
        assertEquals(ResearchBackend.QWEN_LOCAL,
                third.researchConversationState().getActiveBackend().orElseThrow());

        skill.executeFollowUp("Vuelve a buscarlo globalmente", snapshot("Explicame mas", third));
        assertEquals("gpt-1", globalRequest.get().continuation().continuationToken());
        assertFalse(globalRequest.get().instructions().contains("exclusivamente tu conocimiento interno"));
        assertTrue(localRequest.get().instructions().contains("sin buscar en Internet"));
    }

    private ConversationSnapshot snapshot(String userText, AssistantResult result) {
        return new ConversationSnapshot(Capability.CURRENT_RESEARCH, userText, result.text(),
                result.continuationToken(), result.researchConversationState());
    }
}

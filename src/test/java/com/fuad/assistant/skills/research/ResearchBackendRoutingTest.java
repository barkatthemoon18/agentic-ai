package com.fuad.assistant.skills.research;

import com.fuad.assistant.AssistantResult;
import com.fuad.assistant.session.ConversationSnapshot;
import com.fuad.enums.Capability;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The backend is derived from the plan's access: web goes to GPT, model knowledge goes to Qwen. */
class ResearchBackendRoutingTest {
    private final AtomicReference<ResearchRequest> globalRequest = new AtomicReference<>();
    private final AtomicReference<ResearchRequest> localRequest = new AtomicReference<>();
    private final QuickResearchEngine global = request -> {
        globalRequest.set(request);
        return new ResearchEngineResult("respuesta global",
                new ResearchBranchState("gpt-1", request.previousMessages()));
    };
    private final QuickResearchEngine local = request -> {
        localRequest.set(request);
        List<ResearchMessage> messages = new ArrayList<>(request.previousMessages());
        messages.add(new ResearchMessage(ResearchMessage.Role.USER, request.query()));
        messages.add(new ResearchMessage(ResearchMessage.Role.ASSISTANT, "respuesta local"));
        return new ResearchEngineResult("respuesta local", new ResearchBranchState(null, messages));
    };

    @ParameterizedTest
    @CsvSource({
            "MODEL_KNOWLEDGE, QUICK, QWEN_LOCAL",
            "MODEL_KNOWLEDGE, DEEP, QWEN_LOCAL",
            "WEB_REQUIRED, QUICK, GPT_API",
            "WEB_REQUIRED, DEEP, GPT_API"
    })
    void accessShouldDecideTheBackendRegardlessOfDepth(ResearchAccess access, ResearchDepth depth,
                                                       ResearchBackend expected) {
        CurrentResearchSkill skill = new CurrentResearchSkill(global, local, query -> new ResearchPlan(access, depth));

        AssistantResult result = skill.execute("consulta");

        assertEquals(expected, result.researchConversationState().getActiveBackend().orElseThrow());
        assertEquals(expected == ResearchBackend.GPT_API, globalRequest.get() != null);
        assertEquals(expected == ResearchBackend.QWEN_LOCAL, localRequest.get() != null);
    }

    @Test
    void planShouldBeClassifiedForEveryTurnIncludingFollowUps() {
        List<String> classified = new ArrayList<>();
        CurrentResearchSkill skill = new CurrentResearchSkill(global, local, query -> {
            classified.add(query);
            return new ResearchPlan(ResearchAccess.MODEL_KNOWLEDGE, ResearchDepth.QUICK);
        });

        AssistantResult first = skill.execute("Explícame RSA");
        skill.executeFollowUp("Profundiza", snapshot("Explícame RSA", first));

        assertEquals(List.of("Explícame RSA", "Profundiza"), classified);
    }

    @Test
    void switchingBackendsShouldPreserveIndependentBranches() {
        CurrentResearchSkill skill = new CurrentResearchSkill(global, local, query ->
                new ResearchPlan(query.startsWith("web") ? ResearchAccess.WEB_REQUIRED : ResearchAccess.MODEL_KNOWLEDGE,
                        ResearchDepth.QUICK));

        AssistantResult first = skill.execute("web: noticias de Alan Turing");
        AssistantResult second = skill.executeFollowUp("local: explícalo sin internet",
                snapshot("web: noticias de Alan Turing", first));

        assertEquals(ResearchBackend.QWEN_LOCAL,
                second.researchConversationState().getActiveBackend().orElseThrow());
        assertEquals("gpt-1", second.researchConversationState()
                .getBranch(ResearchBackend.GPT_API).orElseThrow().continuationToken());
        assertNull(localRequest.get().continuation().continuationToken());
        assertEquals(2, localRequest.get().previousMessages().size());
        assertEquals("respuesta global", localRequest.get().previousMessages().get(1).content());

        AssistantResult third = skill.executeFollowUp("local: explícame más",
                snapshot("local: explícalo sin internet", second));
        assertEquals(4, localRequest.get().previousMessages().size());
        assertEquals(ResearchBackend.QWEN_LOCAL,
                third.researchConversationState().getActiveBackend().orElseThrow());

        skill.executeFollowUp("web: vuelve a buscarlo", snapshot("local: explícame más", third));
        assertEquals("gpt-1", globalRequest.get().continuation().continuationToken());
        assertEquals(ResearchAccess.WEB_REQUIRED, globalRequest.get().access());
        assertFalse(globalRequest.get().instructions().contains("exclusivamente tu conocimiento interno"));
        assertTrue(localRequest.get().instructions().contains("sin buscar en Internet"));
    }

    private ConversationSnapshot snapshot(String userText, AssistantResult result) {
        return new ConversationSnapshot(Capability.CURRENT_RESEARCH, userText, result.text(),
                result.continuationToken(), result.researchConversationState());
    }
}

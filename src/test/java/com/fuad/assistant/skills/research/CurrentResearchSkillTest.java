package com.fuad.assistant.skills.research;

import com.fuad.assistant.AssistantResult;
import com.fuad.assistant.local.LocalQwenException;
import com.fuad.assistant.session.ConversationSnapshot;
import com.fuad.enums.Capability;
import com.fuad.pipeline.ConversationPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class CurrentResearchSkillTest {
    private final AtomicReference<ResearchRequest> web = new AtomicReference<>();
    private final AtomicReference<ResearchRequest> local = new AtomicReference<>();
    private final QuickResearchEngine webEngine = request -> {
        web.set(request);
        return new ResearchEngineResult("respuesta web",
                new ResearchBranchState("token-web", request.previousMessages()));
    };
    private final QuickResearchEngine localEngine = request -> {
        local.set(request);
        return new ResearchEngineResult("respuesta local",
                new ResearchBranchState(null, request.previousMessages()));
    };

    private CurrentResearchSkill skill(ResearchAccess access, ResearchDepth depth) {
        return new CurrentResearchSkill(webEngine, localEngine, query -> new ResearchPlan(access, depth));
    }

    @Test
    void quickWebResearchShouldBuildVoiceRequestAndKeepConversationOpen() {
        CurrentResearchSkill skill = skill(ResearchAccess.WEB_REQUIRED, ResearchDepth.QUICK);

        AssistantResult result = skill.execute("precio actual de Bitcoin");

        assertEquals("respuesta web", result.text());
        assertEquals("token-web", result.continuationToken());
        assertEquals("precio actual de Bitcoin", web.get().query());
        assertEquals(ResearchAccess.WEB_REQUIRED, web.get().access());
        assertEquals(ResearchDepth.QUICK, web.get().depth());
        assertEquals(1500, web.get().maxOutputTokens());
        assertNull(web.get().continuation().continuationToken());
        assertTrue(web.get().instructions().contains("busca informacion actual en Internet"));
        assertTrue(web.get().instructions().contains("1 a 3 frases"));
        assertNull(local.get(), "web plans must not use the local engine");
        assertEquals(ResearchBackend.GPT_API, result.researchConversationState().getActiveBackend().orElseThrow());
        assertEquals(ConversationPolicy.KEEP_OPEN, skill.getConversationPolicy());
    }

    @Test
    void deepWebResearchShouldForwardContinuationTokenAndUseLargerBudget() {
        CurrentResearchSkill skill = skill(ResearchAccess.WEB_REQUIRED, ResearchDepth.DEEP);

        AssistantResult result = skill.execute("compara las noticias", "token-anterior");

        assertEquals("respuesta web", result.text());
        assertEquals(ResearchAccess.WEB_REQUIRED, web.get().access());
        assertEquals(ResearchDepth.DEEP, web.get().depth());
        assertEquals(4000, web.get().maxOutputTokens());
        assertEquals("token-anterior", web.get().continuation().continuationToken());
        assertTrue(web.get().instructions().contains("Contrasta varias fuentes"));
        assertNull(local.get());
    }

    @ParameterizedTest
    @CsvSource({"QUICK, 1500, 1 a 3 frases", "DEEP, 4000, un maximo de 6 frases"})
    void modelKnowledgeResearchShouldUseTheLocalEngineWithoutWebInstructions(
            ResearchDepth depth, int expectedTokens, String expectedInstruction) {
        CurrentResearchSkill skill = skill(ResearchAccess.MODEL_KNOWLEDGE, depth);

        AssistantResult result = skill.execute("compara Raft y Paxos");

        assertEquals("respuesta local", result.text());
        assertEquals(ResearchAccess.MODEL_KNOWLEDGE, local.get().access());
        assertEquals(depth, local.get().depth());
        assertEquals(expectedTokens, local.get().maxOutputTokens());
        assertTrue(local.get().instructions().contains("sin buscar en Internet"));
        assertTrue(local.get().instructions().contains(expectedInstruction));
        assertNull(web.get(), "knowledge plans must not use the web engine");
        assertEquals(ResearchBackend.QWEN_LOCAL, result.researchConversationState().getActiveBackend().orElseThrow());
    }

    @Test
    void escalationFromGeneralShouldSeedVisibleContextAndIgnoreForeignToken() {
        CurrentResearchSkill skill = skill(ResearchAccess.WEB_REQUIRED, ResearchDepth.QUICK);
        ConversationSnapshot generalSnapshot = new ConversationSnapshot(
                Capability.GENERAL, "¿Quién fue Alan Turing?", "Fue un matemático.",
                "general-gpt-token");

        skill.executeFollowUp("Ahora búscalo en Internet", generalSnapshot);

        assertNull(web.get().continuation().continuationToken());
        assertEquals(2, web.get().previousMessages().size());
        assertEquals("¿Quién fue Alan Turing?", web.get().previousMessages().getFirst().content());
        assertEquals("Fue un matemático.", web.get().previousMessages().getLast().content());
    }

    @Test
    void unavailableLocalResearchShouldReturnSpecificMessageAndPreserveConversation() {
        QuickResearchEngine unavailable = request -> {
            throw new LocalQwenException(LocalQwenException.Kind.UNAVAILABLE, "not ready");
        };
        CurrentResearchSkill skill = new CurrentResearchSkill(
                request -> { throw new AssertionError("web engine must not be used"); },
                unavailable,
                query -> new ResearchPlan(ResearchAccess.MODEL_KNOWLEDGE, ResearchDepth.QUICK));

        AssistantResult result = skill.execute("explica RSA");

        assertEquals("El modelo local no está disponible en este momento.", result.text());
        assertEquals(ConversationPolicy.PRESERVE, result.conversationPolicyOverride());
    }

    @Test
    void nonUnavailableLocalFailuresAndWebFailuresShouldPropagate() {
        LocalQwenException failure = new LocalQwenException(LocalQwenException.Kind.FAILURE, "boom");
        CurrentResearchSkill local = new CurrentResearchSkill(
                request -> { throw new AssertionError("web engine must not be used"); },
                request -> { throw failure; },
                query -> new ResearchPlan(ResearchAccess.MODEL_KNOWLEDGE, ResearchDepth.QUICK));
        LocalQwenException unavailable = new LocalQwenException(LocalQwenException.Kind.UNAVAILABLE, "not ready");
        CurrentResearchSkill web = new CurrentResearchSkill(
                request -> { throw unavailable; },
                request -> { throw new AssertionError("local engine must not be used"); },
                query -> new ResearchPlan(ResearchAccess.WEB_REQUIRED, ResearchDepth.QUICK));

        assertSame(failure, assertThrows(LocalQwenException.class, () -> local.execute("explica RSA")));
        assertSame(unavailable, assertThrows(LocalQwenException.class, () -> web.execute("noticias de hoy")));
    }

    @Test
    void shouldRejectNullCollaborators() {
        ResearchPlanClassifier classifier = query -> new ResearchPlan(ResearchAccess.WEB_REQUIRED, ResearchDepth.QUICK);

        assertThrows(NullPointerException.class, () -> new CurrentResearchSkill(null, localEngine, classifier));
        assertThrows(NullPointerException.class, () -> new CurrentResearchSkill(webEngine, null, classifier));
        assertThrows(NullPointerException.class, () -> new CurrentResearchSkill(webEngine, localEngine, null));
    }
}

package com.fuad.assistant.skills.research;

import com.fuad.assistant.AssistantResult;
import com.fuad.assistant.local.LocalQwenException;
import com.fuad.assistant.session.ConversationSnapshot;
import com.fuad.enums.Capability;
import com.fuad.enums.ConversationPolicy;
import com.fuad.enums.ResearchDepth;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurrentResearchSkillTest {

    @Test
    void quickResearchShouldBuildVoiceRequestAndKeepConversationOpen() {
        AtomicReference<ResearchRequest> captured = new AtomicReference<>();
        QuickResearchEngine engine = request -> {
            captured.set(request);
            return new ResearchEngineResult("respuesta",
                    new ResearchBranchState("token-nuevo", request.previousMessages()));
        };
        CurrentResearchSkill skill = new CurrentResearchSkill(engine, engine,
                query -> ResearchDepth.QUICK, new DefaultResearchBackendClassifier());

        AssistantResult result = skill.execute("precio actual de Bitcoin");

        assertEquals("respuesta", result.getText());
        assertEquals("token-nuevo", result.getContinuationToken());
        assertEquals("precio actual de Bitcoin", captured.get().query());
        assertEquals(ResearchDepth.QUICK, captured.get().depth());
        assertEquals(500, captured.get().maxOutputTokens());
        assertNull(captured.get().continuation().continuationToken());
        assertTrue(captured.get().instructions().contains("1 a 3 frases"));
        assertEquals(ConversationPolicy.KEEP_OPEN, skill.getConversationPolicy());
    }

    @Test
    void deepResearchShouldForwardContinuationTokenAndUseLargerBudget() {
        AtomicReference<ResearchRequest> captured = new AtomicReference<>();
        QuickResearchEngine engine = request -> {
            captured.set(request);
            return new ResearchEngineResult("analisis",
                    new ResearchBranchState("token-siguiente", request.previousMessages()));
        };
        CurrentResearchSkill skill = new CurrentResearchSkill(engine, engine,
                query -> ResearchDepth.DEEP, new DefaultResearchBackendClassifier());

        AssistantResult result = skill.execute("compara las noticias", "token-anterior");

        assertEquals("analisis", result.getText());
        assertEquals(ResearchDepth.DEEP, captured.get().depth());
        assertEquals(1200, captured.get().maxOutputTokens());
        assertEquals("token-anterior", captured.get().continuation().continuationToken());
        assertTrue(captured.get().instructions().contains("Contrasta varias fuentes"));
    }

    @Test
    void escalationFromGeneralShouldSeedVisibleContextAndIgnoreForeignToken() {
        AtomicReference<ResearchRequest> captured = new AtomicReference<>();
        QuickResearchEngine engine = request -> {
            captured.set(request);
            return new ResearchEngineResult("investigación",
                    new ResearchBranchState("research-token", request.previousMessages()));
        };
        CurrentResearchSkill skill = new CurrentResearchSkill(engine, engine,
                query -> ResearchDepth.QUICK, (query, inherited) -> ResearchBackend.GPT_WEB);
        ConversationSnapshot generalSnapshot = new ConversationSnapshot(
                Capability.GENERAL, "¿Quién fue Alan Turing?", "Fue un matemático.",
                "general-gpt-token");

        skill.executeFollowUp("Ahora búscalo en Internet", generalSnapshot);

        assertNull(captured.get().continuation().continuationToken());
        assertEquals(2, captured.get().previousMessages().size());
        assertEquals("¿Quién fue Alan Turing?", captured.get().previousMessages().getFirst().content());
        assertEquals("Fue un matemático.", captured.get().previousMessages().getLast().content());
    }

    @Test
    void unavailableLocalResearchShouldReturnSpecificMessageAndPreserveConversation() {
        QuickResearchEngine unavailable = request -> {
            throw new LocalQwenException(LocalQwenException.Kind.UNAVAILABLE, "not ready");
        };
        CurrentResearchSkill skill = new CurrentResearchSkill(
                request -> { throw new AssertionError("web engine must not be used"); },
                unavailable,
                query -> ResearchDepth.QUICK,
                (query, inherited) -> ResearchBackend.QWEN_LOCAL);

        AssistantResult result = skill.execute("investiga localmente");

        assertEquals("El modelo local no está disponible en este momento.", result.getText());
        assertEquals(ConversationPolicy.PRESERVE, result.getConversationPolicyOverride());
    }
}

package com.fuad.assistant.skills.research;

import com.fuad.assistant.AssistantResult;
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
}

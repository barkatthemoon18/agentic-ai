package com.fuad.assistant.skills.research;

import com.fuad.assistant.AssistantRequest;
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
        AtomicReference<AssistantRequest> captured = new AtomicReference<>();
        CurrentResearchSkill skill = new CurrentResearchSkill(request -> {
            captured.set(request);
            return new AssistantResult("respuesta", "token-nuevo");
        }, query -> ResearchDepth.QUICK);

        AssistantResult result = skill.execute("precio actual de Bitcoin");

        assertEquals("respuesta", result.getText());
        assertEquals("token-nuevo", result.getContinuationToken());
        assertEquals("precio actual de Bitcoin", captured.get().getCommand());
        assertEquals(ResearchDepth.QUICK, captured.get().getResearchDepth());
        assertEquals(500, captured.get().getMaxOutputTokens());
        assertNull(captured.get().getContinuationToken());
        assertTrue(captured.get().getInstructions().contains("1 a 3 frases"));
        assertEquals(ConversationPolicy.KEEP_OPEN, skill.getConversationPolicy());
    }

    @Test
    void deepResearchShouldForwardContinuationTokenAndUseLargerBudget() {
        AtomicReference<AssistantRequest> captured = new AtomicReference<>();
        CurrentResearchSkill skill = new CurrentResearchSkill(request -> {
            captured.set(request);
            return new AssistantResult("analisis", "token-siguiente");
        }, query -> ResearchDepth.DEEP);

        AssistantResult result = skill.execute("compara las noticias", "token-anterior");

        assertEquals("analisis", result.getText());
        assertEquals(ResearchDepth.DEEP, captured.get().getResearchDepth());
        assertEquals(1200, captured.get().getMaxOutputTokens());
        assertEquals("token-anterior", captured.get().getContinuationToken());
        assertTrue(captured.get().getInstructions().contains("Contrasta varias fuentes"));
    }
}

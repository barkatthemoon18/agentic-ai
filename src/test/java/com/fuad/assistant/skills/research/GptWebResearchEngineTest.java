package com.fuad.assistant.skills.research;

import com.fuad.assistant.AssistantRequest;
import com.fuad.assistant.AssistantResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class GptWebResearchEngineTest {
    private final AtomicReference<AssistantRequest> captured = new AtomicReference<>();
    private final GptWebResearchEngine engine = new GptWebResearchEngine(request -> {
        captured.set(request);
        return new AssistantResult("research answer", "next-token");
    });
    private final List<ResearchMessage> history = List.of(
            new ResearchMessage(ResearchMessage.Role.USER, "previous question"),
            new ResearchMessage(ResearchMessage.Role.ASSISTANT, "previous answer"));

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void missingUsableTokenShouldSeedVisibleHistoryAndPreserveDepth(String token) {
        ResearchEngineResult result = engine.research(new ResearchRequest("current query", "instructions", 1200,
                ResearchDepth.DEEP, new ResearchBranchState(token, history)));
        assertTrue(captured.get().command().contains("Usuario: previous question"));
        assertTrue(captured.get().command().contains("Asistente: previous answer"));
        assertTrue(captured.get().command().endsWith("Consulta actual: current query"));
        assertEquals(ResearchDepth.DEEP, captured.get().researchDepth());
        assertEquals("instructions", captured.get().instructions());
        assertEquals(1200, captured.get().maxOutputTokens());
        assertEquals("research answer", result.text());
        assertEquals("next-token", result.continuation().continuationToken());
    }

    @Test
    void continuationTokenShouldAvoidReplayingVisibleHistory() {
        engine.research(new ResearchRequest("current query", "instructions", 500,
                ResearchDepth.QUICK, new ResearchBranchState("previous-token", history)));
        assertEquals("current query", captured.get().command());
        assertEquals("previous-token", captured.get().continuationToken());
        assertEquals(ResearchDepth.QUICK, captured.get().researchDepth());
    }

    @Test
    void newResearchWithoutHistoryShouldSendOnlyCurrentQuery() {
        engine.research(new ResearchRequest("current query", "instructions", 500, ResearchDepth.QUICK, null));
        assertEquals("current query", captured.get().command());
        assertNull(captured.get().continuationToken());
    }
}

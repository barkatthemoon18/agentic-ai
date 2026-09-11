package com.fuad.assistant.skills.research;

import com.fuad.assistant.local.LocalQwenChatClient;
import com.fuad.enums.ResearchDepth;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QwenLocalResearchEngineTest {
    @Test
    @SuppressWarnings("unchecked")
    void shouldDelegateChatAndAppendResearchHistory() {
        LocalQwenChatClient client = mock(LocalQwenChatClient.class);
        when(client.chat(eq("instrucciones"), anyList(), eq(500)))
                .thenReturn("respuesta local");
        QwenLocalResearchEngine engine = new QwenLocalResearchEngine(client);
        List<ResearchMessage> previous = List.of(
                new ResearchMessage(ResearchMessage.Role.USER, "pregunta anterior"),
                new ResearchMessage(ResearchMessage.Role.ASSISTANT, "respuesta anterior"));
        ResearchRequest request = new ResearchRequest(
                "pregunta actual", "instrucciones", 500, ResearchDepth.QUICK,
                new ResearchBranchState(null, previous));

        ResearchEngineResult result = engine.research(request);

        ArgumentCaptor<List<LocalQwenChatClient.Message>> messages =
                ArgumentCaptor.forClass(List.class);
        verify(client).chat(eq("instrucciones"), messages.capture(), eq(500));
        assertEquals(List.of(
                new LocalQwenChatClient.Message(LocalQwenChatClient.Role.USER, "pregunta anterior"),
                new LocalQwenChatClient.Message(LocalQwenChatClient.Role.ASSISTANT, "respuesta anterior"),
                new LocalQwenChatClient.Message(LocalQwenChatClient.Role.USER, "pregunta actual")),
                messages.getValue());
        assertEquals("respuesta local", result.text());
        assertEquals(List.of(
                previous.get(0),
                previous.get(1),
                new ResearchMessage(ResearchMessage.Role.USER, "pregunta actual"),
                new ResearchMessage(ResearchMessage.Role.ASSISTANT, "respuesta local")),
                result.continuation().messages());
        assertEquals(2, previous.size());
    }
}

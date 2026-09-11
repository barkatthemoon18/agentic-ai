package com.fuad.assistant.skills.general;

import com.fuad.assistant.AssistantRequest;
import com.fuad.assistant.AssistantResult;
import com.fuad.assistant.local.LocalQwenChatClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GeneralEnginesTest {
    @Test
    @SuppressWarnings("unchecked")
    void qwenShouldTranslateAndAppendLocalHistory() {
        LocalQwenChatClient client = mock(LocalQwenChatClient.class);
        when(client.chat(anyString(), anyList(), anyInt())).thenReturn("respuesta local");
        QwenGeneralEngine engine = new QwenGeneralEngine(client);
        GeneralBranchState branch = new GeneralBranchState(null, List.of(
                new GeneralMessage(GeneralMessage.Role.USER, "pregunta anterior"),
                new GeneralMessage(GeneralMessage.Role.ASSISTANT, "respuesta anterior")));

        GeneralEngineResult result = engine.respond(new GeneralRequest(
                "pregunta actual", "instrucciones", 300, branch));

        ArgumentCaptor<List<LocalQwenChatClient.Message>> messages = ArgumentCaptor.forClass(List.class);
        verify(client).chat(anyString(), messages.capture(), anyInt());
        assertEquals(3, messages.getValue().size());
        assertEquals("pregunta actual", messages.getValue().getLast().content());
        assertEquals(4, result.continuation().messages().size());
        assertNull(result.continuation().continuationToken());
    }

    @Test
    void gptShouldSeedAChainWithVisibleMessagesWhenNoTokenExists() {
        AtomicReference<AssistantRequest> captured = new AtomicReference<>();
        GptGeneralEngine engine = new GptGeneralEngine(request -> {
            captured.set(request);
            return new AssistantResult("respuesta GPT", "gpt-next");
        });
        GeneralBranchState branch = new GeneralBranchState(null, List.of(
                new GeneralMessage(GeneralMessage.Role.USER, "pregunta anterior"),
                new GeneralMessage(GeneralMessage.Role.ASSISTANT, "respuesta anterior")));

        GeneralEngineResult result = engine.respond(new GeneralRequest(
                "profundiza", "instrucciones", 300, branch));

        assertTrue(captured.get().getCommand().contains("pregunta anterior"));
        assertTrue(captured.get().getCommand().contains("respuesta anterior"));
        assertTrue(captured.get().getCommand().contains("profundiza"));
        assertNull(captured.get().getContinuationToken());
        assertEquals("gpt-next", result.continuation().continuationToken());
    }
}

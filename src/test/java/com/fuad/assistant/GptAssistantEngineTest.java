package com.fuad.assistant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import com.fuad.enums.ResearchDepth;
import com.openai.client.OpenAIClient;
import com.openai.models.ChatModel;
import com.openai.models.ReasoningEffort;
import com.openai.models.responses.*;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GptAssistantEngineTest {
    private final OpenAIClient client = mock(OpenAIClient.class, RETURNS_DEEP_STUBS);
    private final GptAssistantEngine engine = new GptAssistantEngine(client);

    @Test
    void responseShouldJoinTextBlocksIgnoreNonMessagesAndReturnContinuationToken() {
        List<ResponseOutputItem> output = List.of(mock(ResponseOutputItem.class), message(" hola", " mundo "));
        Response response = response("next-token", output);
        when(client.responses().create(any(ResponseCreateParams.class))).thenReturn(response);
        AssistantResult result = engine.process(new AssistantRequest("command", "instructions", 300, null));
        assertEquals("hola mundo", result.getText());
        assertEquals("next-token", result.getContinuationToken());
        ArgumentCaptor<ResponseCreateParams> params = ArgumentCaptor.forClass(ResponseCreateParams.class);
        verify(client.responses()).create(params.capture());
        assertEquals("command", params.getValue().input().orElseThrow().asText());
        assertEquals(Optional.of("instructions"), params.getValue().instructions());
        assertEquals(Optional.of(300L), params.getValue().maxOutputTokens());
        assertEquals(ChatModel.GPT_5_6_LUNA, params.getValue().model().orElseThrow().asChat());
        assertTrue(params.getValue().previousResponseId().isEmpty());
        assertTrue(params.getValue().tools().orElse(List.of()).isEmpty());
        assertTrue(params.getValue().reasoning().isEmpty());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "previous-token"})
    void continuationShouldComeOnlyFromCurrentRequest(String token) {
        Response response = response("returned-token", List.of(message("answer")));
        when(client.responses().create(any(ResponseCreateParams.class))).thenReturn(response);
        engine.process(new AssistantRequest("command", "instructions", 300, token));
        engine.process(new AssistantRequest("independent", "instructions", 300, null));
        ArgumentCaptor<ResponseCreateParams> params = ArgumentCaptor.forClass(ResponseCreateParams.class);
        verify(client.responses(), times(2)).create(params.capture());
        assertEquals(token == null || token.isBlank() ? Optional.empty() : Optional.of(token),
                params.getAllValues().getFirst().previousResponseId());
        assertTrue(params.getAllValues().getLast().previousResponseId().isEmpty(), "A new request must not inherit engine state");
    }

    @ParameterizedTest
    @EnumSource(value = ResearchDepth.class, names = {"QUICK", "DEEP"})
    void researchShouldRequireWebSearchWithDepthSpecificBudgets(ResearchDepth depth) {
        Response response = response("token", List.of(message("research")));
        when(client.responses().create(any(ResponseCreateParams.class))).thenReturn(response);
        engine.process(new AssistantRequest("query", "instructions", 1200, "previous", depth));
        ArgumentCaptor<ResponseCreateParams> params = ArgumentCaptor.forClass(ResponseCreateParams.class);
        verify(client.responses()).create(params.capture());
        boolean deep = depth == ResearchDepth.DEEP;
        var value = params.getValue();
        assertEquals(ToolChoiceOptions.REQUIRED, value.toolChoice().orElseThrow().asOptions());
        assertEquals(Optional.of(deep ? 6L : 2L), value.maxToolCalls());
        assertEquals(Optional.of(deep ? ReasoningEffort.HIGH : ReasoningEffort.LOW), value.reasoning().orElseThrow().effort());
        assertEquals(1, value.tools().orElseThrow().size());
        WebSearchTool search = value.tools().orElseThrow().getFirst().asWebSearch();
        assertEquals(Optional.of(deep ? WebSearchTool.SearchContextSize.HIGH : WebSearchTool.SearchContextSize.LOW), search.searchContextSize());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\n"})
    void blankAssistantTextShouldBeRejected(String text) {
        Response response = response("token", List.of(message(text)));
        when(client.responses().create(any(ResponseCreateParams.class))).thenReturn(response);
        assertThrows(IllegalStateException.class, () -> engine.process(new AssistantRequest("command", "instructions", 300, null)));
    }

    @Test
    void absentMessagesShouldBeRejectedAndClientErrorsShouldPropagate() {
        Response response = response("token", List.of(mock(ResponseOutputItem.class)));
        IllegalStateException failure = new IllegalStateException("transport failure");
        when(client.responses().create(any(ResponseCreateParams.class))).thenReturn(response).thenThrow(failure);
        AssistantRequest request = new AssistantRequest("command", "instructions", 300, null);
        assertThrows(IllegalStateException.class, () -> engine.process(request));
        assertSame(failure, assertThrows(IllegalStateException.class, () -> engine.process(request)));
    }

    private static Response response(String id, List<ResponseOutputItem> output) {
        Response response = mock(Response.class);
        when(response.id()).thenReturn(id);
        when(response.output()).thenReturn(output);
        return response;
    }

    private static ResponseOutputItem message(String... texts) {
        List<ResponseOutputMessage.Content> content = new ArrayList<>();
        for (String text : texts) {
            ResponseOutputText outputText = mock(ResponseOutputText.class);
            when(outputText.text()).thenReturn(text);
            ResponseOutputMessage.Content item = mock(ResponseOutputMessage.Content.class);
            when(item.outputText()).thenReturn(Optional.of(outputText));
            content.add(item);
        }
        ResponseOutputMessage message = mock(ResponseOutputMessage.class);
        when(message.content()).thenReturn(content);
        ResponseOutputItem item = mock(ResponseOutputItem.class);
        when(item.message()).thenReturn(Optional.of(message));
        return item;
    }

    @Test
    void engineShouldContainOnlyInfrastructureState() {
        List<Field> instanceFields = List.of(GptAssistantEngine.class.getDeclaredFields()).stream()
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .toList();

        assertEquals(List.of("client"), instanceFields.stream().map(Field::getName).toList());
    }
}

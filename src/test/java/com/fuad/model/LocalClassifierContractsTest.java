package com.fuad.model;

import com.fuad.activation.wake.LocalWakeClassifier;
import com.fuad.assistant.routing.LocalSemanticRouter;
import com.fuad.enums.Capability;
import com.fuad.vad.WakeResolution;
import com.openai.client.OpenAIClient;
import com.openai.models.ChatModel;
import com.openai.models.chat.completions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LocalClassifierContractsTest {
    private final OpenAIClient client = mock(OpenAIClient.class, RETURNS_DEEP_STUBS);

    @ParameterizedTest
    @CsvSource({"system-time,SYSTEM_TIME", "audio-control,AUDIO_CONTROL", "media-control,MEDIA_CONTROL", "os-command,OS_COMMAND",
            "current-research,CURRENT_RESEARCH", "GENERAL,GENERAL"})
    void routerShouldParseCurrentCapabilitiesAndSendDeterministicModelRequest(String output, Capability expected) {
        respond(output);
        assertEquals(expected, new LocalSemanticRouter(client, " test-model ").classify("abre Firefox"));
        var params = captured();
        assertEquals(ChatModel.of("test-model"), params.model());
        assertEquals(Optional.of(0.0), params.temperature());
        assertEquals(Optional.of(8L), params.maxCompletionTokens());
        assertEquals(2, params.messages().size());
        String input = params.messages().getLast().asUser().content().asText();
        assertTrue(input.contains("<query>\nabre Firefox\n</query>"));
        assertTrue(input.contains("media-control"));
    }

    @ParameterizedTest
    @CsvSource({"wake,WAKE", "INTENT,SEMANTIC_INTENT", "none,NONE"})
    void wakeClassifierShouldMapLabelsAndPreserveCandidateAndRemainder(String output, WakeResolution expected) {
        respond(output);
        assertEquals(expected, new LocalWakeClassifier(client, "test-model").classify("Oeres", "abre Firefox"));
        var params = captured();
        assertEquals(Optional.of(0.0), params.temperature());
        assertEquals(Optional.of(4L), params.maxCompletionTokens());
        String input = params.messages().getLast().asUser().content().asText();
        assertTrue(input.contains("candidate: Oeres"));
        assertTrue(input.contains("remainder: abre Firefox"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "unsupported-label"})
    void classifiersShouldRejectMissingBlankOrUnknownLabels(String output) {
        respond(output);
        assertThrows(IllegalStateException.class, () -> new LocalSemanticRouter(client, "model").classify("command"));
        assertThrows(IllegalStateException.class, () -> new LocalWakeClassifier(client, "model").classify("candidate", "command"));
    }

    @Test
    void quotedLeadingLabelsShouldUseSharedLocalOutputContract() {
        respond("```text\ncurrent-research\n```");
        assertEquals(Capability.CURRENT_RESEARCH, new LocalSemanticRouter(client, "model").classify("investiga"));
        respond("'wake'");
        assertEquals(WakeResolution.WAKE, new LocalWakeClassifier(client, "model").classify("Oeres", "abre Firefox"));
    }

    @Test
    void routerShouldAllowMediaControlWhenRecoveringAnInvalidClassification() {
        ChatCompletion invalid = completion("respuesta libre");
        ChatCompletion valid = completion("media-control");
        when(client.chat().completions().create(any(ChatCompletionCreateParams.class)))
                .thenReturn(invalid, valid);

        assertEquals(Capability.MEDIA_CONTROL,
                new LocalSemanticRouter(client, "model").classify("Pausa la música"));

        ArgumentCaptor<ChatCompletionCreateParams> params = ArgumentCaptor.forClass(ChatCompletionCreateParams.class);
        verify(client.chat().completions(), times(2)).create(params.capture());
        ChatCompletionCreateParams retry = params.getAllValues().getLast();
        assertEquals(4, retry.messages().size());
        assertEquals("respuesta libre", retry.messages().get(2).asAssistant().content().orElseThrow().asText());
        assertTrue(retry.messages().getLast().asUser().content().asText().contains("media-control"));
        assertTrue(retry.messages().get(1).asUser().content().asText().contains("<query>\nPausa la música\n</query>"));
    }

    private void respond(String text) {
        ChatCompletion completion = completion(text);
        when(client.chat().completions().create(any(ChatCompletionCreateParams.class))).thenReturn(completion);
    }

    private ChatCompletion completion(String text) {
        ChatCompletionMessage message = mock(ChatCompletionMessage.class);
        when(message.content()).thenReturn(Optional.ofNullable(text));
        ChatCompletion.Choice choice = mock(ChatCompletion.Choice.class);
        when(choice.message()).thenReturn(message);
        ChatCompletion completion = mock(ChatCompletion.class);
        when(completion.choices()).thenReturn(List.of(choice));
        return completion;
    }

    private ChatCompletionCreateParams captured() {
        ArgumentCaptor<ChatCompletionCreateParams> params = ArgumentCaptor.forClass(ChatCompletionCreateParams.class);
        verify(client.chat().completions()).create(params.capture());
        return params.getValue();
    }
}

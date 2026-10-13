package com.fuad.activation.utterance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuad.enums.UtteranceDecision;
import com.fuad.enums.Capability;
import com.fuad.assistant.session.ConversationSnapshot;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessage;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LocalUtteranceClassifierTest {
    private final OpenAIClient client = mock(OpenAIClient.class, RETURNS_DEEP_STUBS);

    @ParameterizedTest
    @ValueSource(strings = {"Siguiente canción.", "Pon la siguiente canción.", "Pausa TIDAL", "Reanuda la reproducción"})
    void recognizedMediaCommandsShouldAvoidInferenceWithOrWithoutContext(String command) {
        LocalUtteranceClassifier classifier = new LocalUtteranceClassifier(client, "model");

        assertEquals(UtteranceDecision.NEW_REQUEST,
                classifier.classify(UtteranceClassificationRequest.withoutContext(command)));
        assertEquals(UtteranceDecision.NEW_REQUEST, classifier.classify(withContext(command)));
        verifyNoInteractions(client);
    }

    @Test
    void invalidFirstOutputShouldRetryWithACleanRequestAndOriginalContext() {
        ChatCompletion invalid = completion("next_song");
        ChatCompletion valid = completion("new_request");
        when(client.chat().completions().create(any(ChatCompletionCreateParams.class))).thenReturn(invalid, valid);
        var request = withContext("Pasa a otra de las pistas");

        assertEquals(UtteranceDecision.NEW_REQUEST,
                new LocalUtteranceClassifier(client, " test-model ").classify(request));

        ArgumentCaptor<ChatCompletionCreateParams> params = ArgumentCaptor.forClass(ChatCompletionCreateParams.class);
        verify(client.chat().completions(), times(2)).create(params.capture());
        var first = params.getAllValues().getFirst();
        var retry = params.getAllValues().getLast();
        assertEquals(2, retry.messages().size());
        assertEquals(first.messages().getLast(), retry.messages().getLast());
        assertTrue(retry.messages().getLast().asUser().content().asText().contains("RSA"));
        String system = retry.messages().getFirst().asSystem().content().asText();
        assertTrue(system.contains("new_request, follow_up, other"));
        assertFalse(system.contains("next_song"));
        assertFalse(retry.messages().getLast().asUser().content().asText().contains("next_song"));
        assertEquals(first.model(), retry.model());
        assertEquals(Optional.of(0.0), retry.temperature());
        assertEquals(Optional.of(8L), retry.maxCompletionTokens());
        verifyNoMoreInteractions(client.chat().completions());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "next_song"})
    void missingOrInvalidContentShouldRecoverWithOneRetry(String output) {
        ChatCompletion invalid = completion(output);
        ChatCompletion valid = completion("new_request");
        when(client.chat().completions().create(any(ChatCompletionCreateParams.class))).thenReturn(invalid, valid);

        assertEquals(UtteranceDecision.NEW_REQUEST, new LocalUtteranceClassifier(client, "model")
                .classify(UtteranceClassificationRequest.withoutContext("Pasa a otra de las pistas")));
        verify(client.chat().completions(), times(2)).create(any(ChatCompletionCreateParams.class));
    }

    @Test
    void emptyChoicesShouldRecoverWithOneRetry() {
        ChatCompletion empty = mock(ChatCompletion.class);
        when(empty.choices()).thenReturn(List.of());
        ChatCompletion valid = completion("new_request");
        when(client.chat().completions().create(any(ChatCompletionCreateParams.class))).thenReturn(empty, valid);

        assertEquals(UtteranceDecision.NEW_REQUEST, new LocalUtteranceClassifier(client, "model")
                .classify(UtteranceClassificationRequest.withoutContext("Pasa a otra de las pistas")));
        verify(client.chat().completions(), times(2)).create(any(ChatCompletionCreateParams.class));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "next_song private output"})
    void twoInvalidOutputsShouldRaiseASanitizedTypedError(String output) {
        String utterance = "Solicitud privada para cambiar de canción";
        ChatCompletion invalid = completion(output);
        when(client.chat().completions().create(any(ChatCompletionCreateParams.class))).thenReturn(invalid);

        InvalidUtteranceClassificationException error = assertThrows(InvalidUtteranceClassificationException.class,
                () -> new LocalUtteranceClassifier(client, " test-model ")
                        .classify(UtteranceClassificationRequest.withoutContext(utterance)));

        assertEquals("test-model", error.model());
        assertEquals(2, error.attempts());
        assertNotNull(error.getCause());
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            assertFalse(cause.getMessage().contains(utterance));
            assertFalse(cause.getMessage().contains("next_song"));
            assertFalse(cause.getMessage().contains("private output"));
        }
        verify(client.chat().completions(), times(2)).create(any(ChatCompletionCreateParams.class));
        verifyNoMoreInteractions(client.chat().completions());
    }

    @Test
    void twoEmptyChoicesShouldRaiseTheTypedError() {
        ChatCompletion empty = mock(ChatCompletion.class);
        when(empty.choices()).thenReturn(List.of());
        when(client.chat().completions().create(any(ChatCompletionCreateParams.class))).thenReturn(empty);

        InvalidUtteranceClassificationException error = assertThrows(InvalidUtteranceClassificationException.class,
                () -> new LocalUtteranceClassifier(client, "model")
                        .classify(UtteranceClassificationRequest.withoutContext("Pasa a otra de las pistas")));

        assertEquals(2, error.attempts());
        verify(client.chat().completions(), times(2)).create(any(ChatCompletionCreateParams.class));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void providerFailuresShouldPropagateWithoutAnAdditionalRetry(boolean afterInvalidOutput) {
        IllegalStateException failure = new IllegalStateException("provider offline");
        ChatCompletion invalid = completion("next_song");
        var response = when(client.chat().completions().create(any(ChatCompletionCreateParams.class)));
        if (afterInvalidOutput) {
            response.thenReturn(invalid).thenThrow(failure);
        } else {
            response.thenThrow(failure);
        }

        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> new LocalUtteranceClassifier(client, "model")
                        .classify(UtteranceClassificationRequest.withoutContext("Pasa a otra de las pistas"))));
        verify(client.chat().completions(), times(afterInvalidOutput ? 2 : 1))
                .create(any(ChatCompletionCreateParams.class));
    }

    @ParameterizedTest
    @CsvSource({"new_request, NEW_REQUEST", "follow_up, FOLLOW_UP", "other, OTHER"})
    void aValidFirstOutputShouldNotRetry(String output, UtteranceDecision expected) {
        ChatCompletion valid = completion(output);
        when(client.chat().completions().create(any(ChatCompletionCreateParams.class))).thenReturn(valid);

        assertEquals(expected, new LocalUtteranceClassifier(client, "model").classify(withContext("Pasa a otra de las pistas")));
        verify(client.chat().completions()).create(any(ChatCompletionCreateParams.class));
        verifyNoMoreInteractions(client.chat().completions());
    }

    @Test
    void modelOnlyModeShouldInferRecognizedMediaCommands() {
        ChatCompletion valid = completion("new_request");
        when(client.chat().completions().create(any(ChatCompletionCreateParams.class))).thenReturn(valid);

        assertEquals(UtteranceDecision.NEW_REQUEST, new LocalUtteranceClassifier(client, "model", false)
                .classify(UtteranceClassificationRequest.withoutContext("Siguiente canción.")));
        verify(client.chat().completions()).create(any(ChatCompletionCreateParams.class));
    }

    @Test
    void shouldBypassRulesOnlyWhenRequestedAndKeepContextProtection() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        List<String> requests = Collections.synchronizedList(new ArrayList<>());
        server.createContext("/v1/chat/completions", exchange -> {
            requests.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = """
                    {"id":"test","object":"chat.completion","created":1,"model":"test-model",
                     "choices":[{"index":0,"message":{"role":"assistant","content":"follow_up"},"finish_reason":"stop"}]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        var client = OpenAIOkHttpClient.builder().baseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1")
                .apiKey("test").maxRetries(0).build();
        try {
            var request = UtteranceClassificationRequest.withoutContext("Explícame RSA");
            assertEquals(UtteranceDecision.NEW_REQUEST, new LocalUtteranceClassifier(client, "test-model").classify(request));
            assertTrue(requests.isEmpty());
            assertEquals(UtteranceDecision.OTHER, new LocalUtteranceClassifier(client, "test-model", false).classify(request));
            var ambient = UtteranceClassificationRequest.withoutContext("La taza está junto al cuaderno.");
            new LocalUtteranceClassifier(client, "test-model", true).classify(ambient);
            assertEquals(2, requests.size());
            var mapper = new ObjectMapper();
            var pure = mapper.readTree(requests.get(0));
            var hybrid = mapper.readTree(requests.get(1));
            assertEquals(pure.get("messages").get(0), hybrid.get("messages").get(0));
            assertEquals(0.0, pure.get("temperature").asDouble());
            assertEquals(8, pure.get("max_completion_tokens").asInt());
        }
        finally {
            client.close();
            server.stop(0);
        }
    }

    private UtteranceClassificationRequest withContext(String command) {
        return UtteranceClassificationRequest.withContext(command,
                new ConversationSnapshot(Capability.GENERAL, "Explícame RSA", "RSA usa dos claves."));
    }

    private ChatCompletion completion(String output) {
        ChatCompletionMessage message = mock(ChatCompletionMessage.class);
        when(message.content()).thenReturn(Optional.ofNullable(output));
        ChatCompletion.Choice choice = mock(ChatCompletion.Choice.class);
        when(choice.message()).thenReturn(message);
        ChatCompletion completion = mock(ChatCompletion.class);
        when(completion.choices()).thenReturn(List.of(choice));
        return completion;
    }
}

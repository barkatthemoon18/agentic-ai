package com.fuad.assistant.skills.research;

import com.openai.client.OpenAIClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessage;
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

class LocalResearchPlanClassifierTest {
    private final OpenAIClient client = mock(OpenAIClient.class, RETURNS_DEEP_STUBS);
    private final LocalResearchPlanClassifier classifier = new LocalResearchPlanClassifier(client, "model");

    private void modelReturns(String... outputs) {
        ChatCompletion first = completion(outputs[0]);
        ChatCompletion[] rest = new ChatCompletion[outputs.length - 1];
        for (int i = 1; i < outputs.length; i++) {
            rest[i - 1] = completion(outputs[i]);
        }
        when(client.chat().completions().create(any(ChatCompletionCreateParams.class))).thenReturn(first, rest);
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

    @ParameterizedTest
    @CsvSource({
            "knowledge_quick, MODEL_KNOWLEDGE, QUICK",
            "knowledge_deep, MODEL_KNOWLEDGE, DEEP",
            "web_quick, WEB_REQUIRED, QUICK",
            "web_deep, WEB_REQUIRED, DEEP",
            "WEB_DEEP, WEB_REQUIRED, DEEP",
            "'web_quick.', WEB_REQUIRED, QUICK"
    })
    void shouldUseTheModelPlanWhenNoExplicitRuleApplies(String output, ResearchAccess access, ResearchDepth depth) {
        modelReturns(output);

        assertEquals(new ResearchPlan(access, depth), classifier.classify("Explícame RSA"));
        verify(client.chat().completions(), times(1)).create(any(ChatCompletionCreateParams.class));
    }

    @Test
    void shouldAcceptCommonModelDecoration() {
        modelReturns("```text\nknowledge_deep\n```");

        assertEquals(new ResearchPlan(ResearchAccess.MODEL_KNOWLEDGE, ResearchDepth.DEEP),
                classifier.classify("Explícame RSA"));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "¿Cuál es el precio actual de Bitcoin?|knowledge_quick|WEB_REQUIRED|QUICK",
            "Busca en internet qué es Kafka|knowledge_quick|WEB_REQUIRED|QUICK",
            "Dame las fuentes de eso|knowledge_deep|WEB_REQUIRED|DEEP",
            "Verifica si Java 21 es LTS|knowledge_quick|WEB_REQUIRED|QUICK",
            "¿Qué noticias hay de NVIDIA?|knowledge_quick|WEB_REQUIRED|QUICK",
            "¿Cuál es la última versión estable de Spring Boot?|knowledge_quick|WEB_REQUIRED|QUICK"
    })
    void webOrFreshnessSignalsShouldForceWebAccessAndKeepTheModelDepth(String query, String modelOutput,
                                                                      ResearchAccess access, ResearchDepth depth) {
        modelReturns(modelOutput);

        assertEquals(new ResearchPlan(access, depth), classifier.classify(query));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Explícame RSA sin internet",
            "Cuál es el precio actual del dólar, sin usar internet",
            "Dame las fuentes pero solo con tu conocimiento",
            "No busques en internet, ¿cuál es la última versión de Java?"
    })
    void explicitNoWebRequestShouldOverrideWebAndFreshnessSignals(String query) {
        modelReturns("web_quick");

        assertEquals(new ResearchPlan(ResearchAccess.MODEL_KNOWLEDGE, ResearchDepth.QUICK), classifier.classify(query));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Compara Raft y Paxos en profundidad",
            "Profundiza en la diferencia entre TCP y QUIC",
            "Investiga a fondo la historia de Unix"
    })
    void explicitDepthShouldForceDeepWithoutChangingAccess(String query) {
        modelReturns("knowledge_quick");

        assertEquals(new ResearchPlan(ResearchAccess.MODEL_KNOWLEDGE, ResearchDepth.DEEP), classifier.classify(query));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Explica brevemente TCP", "Dame una respuesta rápida sobre TCP", "TCP en pocas palabras"})
    void explicitBrevityShouldForceQuickWithoutChangingAccess(String query) {
        modelReturns("knowledge_deep");

        assertEquals(new ResearchPlan(ResearchAccess.MODEL_KNOWLEDGE, ResearchDepth.QUICK), classifier.classify(query));
    }

    @Test
    void explicitDeepShouldWinOverExplicitQuick() {
        modelReturns("knowledge_quick");

        assertEquals(ResearchDepth.DEEP, classifier.classify("Explica brevemente TCP y profundiza en QUIC").depth());
    }

    @Test
    void shouldSendOnlyTheNormalizedQueryAsDataWithDeterministicSettings() {
        modelReturns("knowledge_quick");

        classifier.classify("  ¿Qué   es AES-GCM?  ");

        ArgumentCaptor<ChatCompletionCreateParams> params = ArgumentCaptor.forClass(ChatCompletionCreateParams.class);
        verify(client.chat().completions()).create(params.capture());
        ChatCompletionCreateParams sent = params.getValue();
        assertEquals(2, sent.messages().size());
        assertTrue(sent.messages().getFirst().asSystem().content().asText().contains("knowledge_quick"));
        String user = sent.messages().getLast().asUser().content().asText();
        assertTrue(user.contains("<query>"));
        assertTrue(user.contains("¿que es aes-gcm?"));
        assertFalse(user.contains("Qué"));
        assertEquals(Optional.of(0.0), sent.temperature());
        assertEquals(Optional.of(12L), sent.maxCompletionTokens());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "medium", "maybe web_quick", "knowledge"})
    void missingOrInvalidOutputShouldRecoverWithOneRetry(String invalid) {
        modelReturns(invalid, "web_deep");

        assertEquals(new ResearchPlan(ResearchAccess.WEB_REQUIRED, ResearchDepth.DEEP), classifier.classify("Explícame RSA"));

        ArgumentCaptor<ChatCompletionCreateParams> params = ArgumentCaptor.forClass(ChatCompletionCreateParams.class);
        verify(client.chat().completions(), times(2)).create(params.capture());
        ChatCompletionCreateParams first = params.getAllValues().getFirst();
        ChatCompletionCreateParams retry = params.getAllValues().getLast();
        assertEquals(2, first.messages().size());
        boolean echoedInvalid = invalid != null && !invalid.isBlank();
        assertEquals(echoedInvalid ? 4 : 3, retry.messages().size());
        assertEquals(first.messages().getFirst(), retry.messages().getFirst());
        assertEquals(first.messages().get(1), retry.messages().get(1));
        assertTrue(retry.messages().getLast().asAssistant().content().orElseThrow().asText()
                .contains("Tu salida anterior fue inválida"));
    }

    @Test
    void twoInvalidOutputsShouldFailAfterExactlyOneRetry() {
        modelReturns("medium", "unknown");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> classifier.classify("Explícame RSA"));

        assertTrue(error.getMessage().contains("invalid after retry"));
        verify(client.chat().completions(), times(2)).create(any(ChatCompletionCreateParams.class));
    }

    @Test
    void rulesShouldNotBypassAnInvalidModelOutput() {
        modelReturns("medium", "unknown");

        assertThrows(IllegalStateException.class, () -> classifier.classify("Busca en internet qué es Kafka"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void blankQueriesShouldBeRejectedBeforeCallingTheModel(String query) {
        assertThrows(IllegalArgumentException.class, () -> classifier.classify(query));
        verifyNoInteractions(client);
    }

    @Test
    void shouldRejectNullDependencies() {
        assertThrows(NullPointerException.class, () -> classifier.classify(null));
        assertThrows(NullPointerException.class, () -> new LocalResearchPlanClassifier(null, "model"));
        assertThrows(NullPointerException.class, () -> new LocalResearchPlanClassifier(client, null));
        assertThrows(IllegalArgumentException.class, () -> new LocalResearchPlanClassifier(client, " "));
    }
}

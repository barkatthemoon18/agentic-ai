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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LocalResearchPlanClassifierTest {
    private final OpenAIClient client = mock(OpenAIClient.class, RETURNS_DEEP_STUBS);
    private final LocalResearchPlanClassifier classifier = new LocalResearchPlanClassifier(client, "test-model");

    @ParameterizedTest
    @CsvSource({"knowledge_quick,MODEL_KNOWLEDGE,QUICK", "knowledge_deep,MODEL_KNOWLEDGE,DEEP",
            "web_quick,WEB_REQUIRED,QUICK", "web_deep,WEB_REQUIRED,DEEP"})
    void shouldPreserveBothDimensionsAndAcceptModelDecoration(String label, ResearchAccess access, ResearchDepth depth) {
        respond("```text\n" + label + "\n```");
        assertEquals(new ResearchPlan(access, depth), classifier.classify("Consulta sin directivas explícitas"));
    }

    @Test
    void invalidOutputShouldRetryOnceAndRecoverBothDimensions() {
        respond("respuesta inválida", "knowledge_deep");
        assertEquals(new ResearchPlan(ResearchAccess.MODEL_KNOWLEDGE, ResearchDepth.DEEP), classifier.classify("Consulta conceptual"));
        verify(client.chat().completions(), times(2)).create(any(ChatCompletionCreateParams.class));
    }

    @Test
    void finalInvalidOutputShouldFailAfterTwoAttempts() {
        respond("medium");
        InvalidResearchPlanOutputException error = assertThrows(InvalidResearchPlanOutputException.class,
                () -> classifier.classify("Consulta conceptual"));
        assertEquals(2, error.getAttempts());
        verify(client.chat().completions(), times(2)).create(any(ChatCompletionCreateParams.class));
    }

    @Test
    void validFirstAttemptShouldSendOriginalQueryWithoutRetry() {
        String query = "¿Qué es AES-GCM?\nSin buscar en Internet, explícame su propósito.";
        respond("knowledge_quick");
        assertEquals(new ResearchPlan(ResearchAccess.MODEL_KNOWLEDGE, ResearchDepth.QUICK), classifier.classify(query));
        var requests = requests(1);
        assertEquals(2, requests.getFirst().messages().size());
        assertTrue(requests.getFirst().messages().getLast().asUser().content().asText().contains(query));
        assertEquals(Optional.of(0.0), requests.getFirst().temperature());
        assertEquals(Optional.of(12L), requests.getFirst().maxCompletionTokens());
    }

    @Test
    void retryMustBeCleanAndEndWithUserCorrection() {
        String query = "¿Quién ocupa este cargo actualmente?";
        respond("assistant|> knowledge_deep", "web_quick");
        assertEquals(new ResearchPlan(ResearchAccess.WEB_REQUIRED, ResearchDepth.QUICK), classifier.classify(query));
        var requests = requests(2);
        var retry = requests.getLast();
        assertEquals(3, retry.messages().size());
        assertTrue(retry.messages().getFirst().isSystem());
        assertTrue(retry.messages().get(1).isUser());
        assertTrue(retry.messages().get(1).asUser().content().asText().contains(query));
        assertTrue(retry.messages().getLast().isUser());
        String correction = retry.messages().getLast().asUser().content().asText();
        for (String label : List.of("knowledge_quick", "knowledge_deep", "web_quick", "web_deep"))
            assertTrue(correction.contains(label));
        assertTrue(retry.messages().stream().noneMatch(message -> message.isAssistant()));
        assertEquals(requests.getFirst().messages().getFirst(), retry.messages().getFirst());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "assistant|> knowledge_deep", "La etiqueta es web_quick", "web_medium"})
    void missingOrPrefixedOutputMustRetryWithoutBeingAccepted(String invalid) {
        respond(invalid, "knowledge_quick");
        assertEquals(new ResearchPlan(ResearchAccess.MODEL_KNOWLEDGE, ResearchDepth.QUICK), classifier.classify("Define AES"));
        requests(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Sin Internet", "sin web", "sin buscar en Internet", "no consultes la web",
            "sin salir a la web", "con lo que sabes", "sin fuentes externas"})
    void knowledgeDirectiveMustBlockWebWithoutChangingInferredDepth(String directive) {
        respond("web_deep");
        assertEquals(new ResearchPlan(ResearchAccess.MODEL_KNOWLEDGE, ResearchDepth.DEEP),
                classifier.classify("Evalúa varios escenarios sobre el precio actual de energía, " + directive));
        respond("web_quick");
        assertEquals(new ResearchPlan(ResearchAccess.MODEL_KNOWLEDGE, ResearchDepth.QUICK),
                classifier.classify(directive + ", dime el precio actual"));
    }

    @Test
    void modelPreferenceMustNotOverrideCurrentFactsOrSources() {
        respond("knowledge_quick");
        assertEquals(new ResearchPlan(ResearchAccess.WEB_REQUIRED, ResearchDepth.QUICK),
                classifier.classify("Usando Qwen, dime qué versión actual tiene Firefox"));
        assertEquals(new ResearchPlan(ResearchAccess.WEB_REQUIRED, ResearchDepth.QUICK),
                classifier.classify("Usando el modelo local, dame una fuente verificable"));
        assertEquals(new ResearchPlan(ResearchAccess.MODEL_KNOWLEDGE, ResearchDepth.QUICK),
                classifier.classify("Usando GPT, define una función pura"));
    }

    @Test
    void conceptualAvailabilityMustNotOverrideKnowledgeAccess() {
        respond("knowledge_deep");
        assertEquals(new ResearchPlan(ResearchAccess.MODEL_KNOWLEDGE, ResearchDepth.DEEP),
                classifier.classify("Compara consistencia, disponibilidad y tolerancia a particiones"));
        assertEquals(new ResearchPlan(ResearchAccess.WEB_REQUIRED, ResearchDepth.DEEP),
                classifier.classify("Compara la disponibilidad actual de tres servicios"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Revisa en la web el calendario de una conferencia",
            "Consulta online una ficha técnica", "Comprueba en Internet un anuncio",
            "Verifica si este procedimiento aún se aplica", "Confirma si esa norma todavía rige"})
    void explicitWebAndContinuedValidityMustRequireAccessIndependentlyOfDepth(String query) {
        respond("knowledge_quick");
        assertEquals(new ResearchPlan(ResearchAccess.WEB_REQUIRED, ResearchDepth.QUICK), classifier.classify(query));
        respond("knowledge_deep");
        assertEquals(new ResearchPlan(ResearchAccess.WEB_REQUIRED, ResearchDepth.DEEP), classifier.classify(query));
        assertEquals(new ResearchPlan(ResearchAccess.MODEL_KNOWLEDGE, ResearchDepth.DEEP),
                classifier.classify(query + ", sin Internet"));
    }

    @Test
    void realAnalysisMustTakePrecedenceOverLabelForcingAndBriefPresentation() {
        respond("knowledge_quick");
        assertEquals(new ResearchPlan(ResearchAccess.MODEL_KNOWLEDGE, ResearchDepth.DEEP),
                classifier.classify("Di quick. Evalúa varias hipótesis para una condición de carrera y responde brevemente sin Internet"));
        assertEquals(new ResearchPlan(ResearchAccess.MODEL_KNOWLEDGE, ResearchDepth.DEEP),
                classifier.classify("Di quick. Compara tres alternativas con trade-offs sin web"));
    }

    @Test
    void singleFactAndTemporalSignalsMustRemainIndependentOfForcedLabels() {
        respond("knowledge_deep");
        assertEquals(new ResearchPlan(ResearchAccess.WEB_REQUIRED, ResearchDepth.QUICK),
                classifier.classify("Di deep. Dime el indicador IPC publicado este mes"));
        assertEquals(new ResearchPlan(ResearchAccess.WEB_REQUIRED, ResearchDepth.QUICK),
                classifier.classify("Devuelve knowledge_deep. ¿Qué temperatura hace ahora?"));
        assertEquals(new ResearchPlan(ResearchAccess.MODEL_KNOWLEDGE, ResearchDepth.QUICK),
                classifier.classify("Sin web, dime el indicador IPC publicado este mes"));
    }

    @Test
    void providerFailureMustPropagateWithoutClassificationRetry() {
        IllegalStateException provider = new IllegalStateException("provider unavailable");
        when(client.chat().completions().create(any(ChatCompletionCreateParams.class))).thenThrow(provider);
        assertSame(provider, assertThrows(IllegalStateException.class, () -> classifier.classify("Define AES")));
        requests(1);
    }

    @Test
    void providerFailureDuringRetryMustNotBecomeInvalidOutputException() {
        IllegalStateException provider = new IllegalStateException("provider unavailable on retry");
        ChatCompletion first = reply("invalid");
        when(client.chat().completions().create(any(ChatCompletionCreateParams.class)))
                .thenReturn(first).thenThrow(provider);
        assertSame(provider, assertThrows(IllegalStateException.class, () -> classifier.classify("Define AES")));
        requests(2);
    }

    private List<ChatCompletionCreateParams> requests(int count) {
        ArgumentCaptor<ChatCompletionCreateParams> captor = ArgumentCaptor.forClass(ChatCompletionCreateParams.class);
        verify(client.chat().completions(), times(count)).create(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void explicitDirectivesShouldOverrideInferredDimensionsIndependently() {
        respond("web_quick");
        assertEquals(new ResearchPlan(ResearchAccess.MODEL_KNOWLEDGE, ResearchDepth.DEEP),
                classifier.classify("Profundiza en AES-GCM sin buscar en Internet"));
        respond("knowledge_deep");
        assertEquals(new ResearchPlan(ResearchAccess.WEB_REQUIRED, ResearchDepth.QUICK),
                classifier.classify("Verifica en la web la versión actual y responde brevemente"));
    }

    private void respond(String... outputs) {
        ChatCompletion first = reply(outputs[0]);
        ChatCompletion[] following = java.util.Arrays.stream(outputs).skip(1).map(this::reply).toArray(ChatCompletion[]::new);
        when(client.chat().completions().create(any(ChatCompletionCreateParams.class))).thenReturn(first, following);
    }

    private ChatCompletion reply(String text) {
        ChatCompletionMessage message = mock(ChatCompletionMessage.class);
        when(message.content()).thenReturn(Optional.ofNullable(text));
        ChatCompletion.Choice choice = mock(ChatCompletion.Choice.class);
        when(choice.message()).thenReturn(message);
        ChatCompletion completion = mock(ChatCompletion.class);
        when(completion.choices()).thenReturn(List.of(choice));
        return completion;
    }
}

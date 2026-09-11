package com.fuad.assistant.skills.general;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalGeneralComplexityClassifierTest {
    @Test
    void shouldParseClosedLabels() {
        assertEquals(GeneralBackend.QWEN_LOCAL, LocalGeneralComplexityClassifier.parse("local"));
        assertEquals(GeneralBackend.GPT, LocalGeneralComplexityClassifier.parse("```text\ngpt\n```"));
    }

    @Test
    void shouldRejectUnknownLabels() {
        assertThrows(IllegalStateException.class,
                () -> LocalGeneralComplexityClassifier.parse("research"));
        assertThrows(IllegalStateException.class,
                () -> LocalGeneralComplexityClassifier.parse("Para responder usaría gpt"));
    }

    @Test
    void validFirstPassShouldNotRetry() {
        AtomicInteger calls = new AtomicInteger();
        LocalGeneralComplexityClassifier classifier = new LocalGeneralComplexityClassifier(request -> {
            calls.incrementAndGet();
            assertFalse(request.retry());
            assertTrue(request.instructions().endsWith("\n"));
            return Optional.of("local");
        });

        GeneralBackendClassification result = classifier.classifyDetailed("¿Qué es DNS?");

        assertEquals(GeneralBackend.QWEN_LOCAL, result.backend());
        assertEquals(1, result.attempts());
        assertTrue(result.firstPassValid());
        assertEquals(1, calls.get());
    }

    @Test
    void invalidFirstPassShouldRetryOnceWithClosedInstruction() {
        Deque<Optional<String>> outputs = new ArrayDeque<>();
        outputs.add(Optional.of("Esta consulta necesita análisis"));
        outputs.add(Optional.of("gpt"));
        List<GeneralBackendInferenceRequest> requests = new ArrayList<>();
        LocalGeneralComplexityClassifier classifier = new LocalGeneralComplexityClassifier(request -> {
            requests.add(request);
            return outputs.removeFirst();
        });

        GeneralBackendClassification result = classifier.classifyDetailed("Analiza tres arquitecturas");

        assertEquals(GeneralBackend.GPT, result.backend());
        assertEquals(2, result.attempts());
        assertTrue(result.retryRecovered());
        assertFalse(requests.getFirst().retry());
        assertTrue(requests.getLast().retry());
        assertEquals("Esta consulta necesita análisis", requests.getLast().previousInvalidOutput());
        assertEquals(List.of("local", "gpt"), requests.getLast().allowedLabels());
    }

    @Test
    void emptyFirstPassShouldRetryWithoutAssistantOutput() {
        AtomicInteger calls = new AtomicInteger();
        LocalGeneralComplexityClassifier classifier = new LocalGeneralComplexityClassifier(request -> {
            calls.incrementAndGet();
            if (!request.retry()) {
                return Optional.empty();
            }
            assertEquals(null, request.previousInvalidOutput());
            return Optional.of("local");
        });

        assertEquals(GeneralBackend.QWEN_LOCAL, classifier.classify("Resume La Odisea"));
        assertEquals(2, calls.get());
    }

    @Test
    void twoInvalidOutputsShouldPropagateAfterOneRetry() {
        AtomicInteger calls = new AtomicInteger();
        LocalGeneralComplexityClassifier classifier = new LocalGeneralComplexityClassifier(request -> {
            calls.incrementAndGet();
            return Optional.of("respuesta libre");
        });

        InvalidGeneralBackendOutputException error = assertThrows(
                InvalidGeneralBackendOutputException.class,
                () -> classifier.classify("Resume La Odisea"));

        assertEquals(2, error.getAttempts());
        assertEquals(2, calls.get());
    }

    @Test
    void providerFailureShouldNotRetry() {
        AtomicInteger calls = new AtomicInteger();
        LocalGeneralComplexityClassifier classifier = new LocalGeneralComplexityClassifier(request -> {
            calls.incrementAndGet();
            throw new IllegalArgumentException("provider offline");
        });

        assertThrows(IllegalArgumentException.class, () -> classifier.classify("Explica RSA"));
        assertEquals(1, calls.get());
    }
}

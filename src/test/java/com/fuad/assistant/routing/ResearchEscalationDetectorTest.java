package com.fuad.assistant.routing;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResearchEscalationDetectorTest {
    private final ResearchEscalationDetector detector = new ResearchEscalationDetector();

    @ParameterizedTest
    @ValueSource(strings = {
            "Ahora búscalo en Internet",
            "Investiga eso",
            "Dame fuentes",
            "Verifica si sigue siendo cierto",
            "¿Qué pasó hoy?",
            "¿Cuál es el precio actual?",
            "Busca las últimas noticias"
    })
    void shouldRecognizeStrongResearchSignals(String command) {
        assertTrue(detector.shouldEscalate(command));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "¿Qué inventos hizo?",
            "Explícamelo mejor",
            "Usa GPT para profundizar",
            "¿Qué es la búsqueda en Internet?",
            "¿Qué fuentes de energía existen?",
            "No lo busques en Internet",
            "Sin investigar, dime qué recuerdas"
    })
    void shouldRejectOrdinaryConceptualAndNegatedRequests(String command) {
        assertFalse(detector.shouldEscalate(command));
    }
}

package com.fuad.assistant.routing;

import com.fuad.enums.Capability;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class GuardedSemanticRouterTest {
    @Test
    void shouldRouteHighConfidenceRequestsWithoutCallingModel() {
        AtomicBoolean called = new AtomicBoolean();
        GuardedSemanticRouter router = new GuardedSemanticRouter(command -> {
            called.set(true);
            return Capability.GENERAL;
        });

        assertEquals(Capability.SYSTEM_TIME, router.classify("¿Qué hora es?"));
        assertEquals(Capability.AUDIO_CONTROL, router.classify("Silencia tu voz"));
        assertEquals(Capability.OS_COMMAND, router.classify("¿Puedes cerrar Spotify?"));
        assertEquals(Capability.OS_COMMAND, router.classify("Enfoca Visual Studio Code"));
        assertEquals(Capability.OS_COMMAND, router.classify("Muéstrame las aplicaciones instaladas"));
        assertEquals(Capability.CURRENT_RESEARCH,
                router.classify("Busca las últimas noticias sobre OpenAI"));
        assertEquals(Capability.CURRENT_RESEARCH,
                router.classify("Busca globalmente quién fue Alan Turing"));
        assertEquals(Capability.CURRENT_RESEARCH,
                router.classify("Busca localmente quién fue Alan Turing"));
        assertEquals(Capability.CURRENT_RESEARCH,
                router.classify("¿Qué ocurrió hoy con NVIDIA?"));
        assertEquals(Capability.CURRENT_RESEARCH,
                router.classify("¿Cuál es el precio de Bitcoin?"));
        assertEquals(Capability.CURRENT_RESEARCH,
                router.classify("¿Cómo estará el clima en Santiago?"));
        assertFalse(called.get());
    }

    @Test
    void shouldRejectIncompatibleSpecializedModelDecisions() {
        assertEquals(Capability.GENERAL,
                new GuardedSemanticRouter(command -> Capability.SYSTEM_TIME).classify("Spotify"));
        assertEquals(Capability.GENERAL,
                new GuardedSemanticRouter(command -> Capability.AUDIO_CONTROL).classify("¿Qué es RSA?"));
        assertEquals(Capability.GENERAL,
                new GuardedSemanticRouter(command -> Capability.SYSTEM_TIME)
                        .classify("¿Qué día fue el 11 de septiembre de 2001?"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Por favor, no busques noticias actuales",
            "No investigues eso"
    })
    void shouldRejectResearchRoutingWhenTheRequestIsExplicitlyNegated(String command) {
        GuardedSemanticRouter router = new GuardedSemanticRouter(ignored -> Capability.CURRENT_RESEARCH);

        assertEquals(Capability.GENERAL, router.classify(command));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Explícame brevemente qué es AES-GCM, sin buscar en Internet",
            "Sin buscar en Internet, explícame brevemente qué es AES-GCM",
            "Profundiza en cómo funciona AES-GCM, sin buscar en Internet",
            "Sin buscar en Internet, profundiza en cómo funciona AES-GCM",
            "Explícame AES sin web", "Sin web, explícame AES",
            "Explícame AES y no lo busques en Internet", "No lo busques en Internet, explícame AES"
    })
    void negatedWebSignalsMustNotRouteExplanationToResearchOrRewriteModelInput(String command) {
        AtomicBoolean called = new AtomicBoolean();
        GuardedSemanticRouter router = new GuardedSemanticRouter(original -> {
            called.set(true);
            assertEquals(command, original);
            return Capability.CURRENT_RESEARCH;
        });
        assertEquals(Capability.GENERAL, router.classify(command));
        org.junit.jupiter.api.Assertions.assertTrue(called.get());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Investiga AES-GCM sin buscar en Internet",
            "Sin buscar en Internet, investiga AES-GCM",
            "Investiga AES-GCM sin web", "Sin web, investiga AES-GCM",
            "Busca AES-GCM en Internet", "Verifica si esto sigue vigente"
    })
    void remainingResearchIntentMustSurviveWebNegation(String command) {
        GuardedSemanticRouter router = new GuardedSemanticRouter(ignored -> {
            throw new AssertionError("Strong research intent must be deterministic");
        });
        assertEquals(Capability.CURRENT_RESEARCH, router.classify(command));
    }
}

package com.fuad.assistant.routing;

import com.fuad.enums.Capability;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.*;

class GuardedSemanticRouterTest {
    @ParameterizedTest
    @ValueSource(strings = {
            "Siguiente canción", "Siguiente pista.", "Canción anterior", "Pista anterior!",
            "Cambia a la siguiente canción", "Cambia a la siguiente pista",
            "Pon la canción anterior", "Pon la pista anterior",
            "Pausa la música", "Pausa la canción", "Pausa la reproducción",
            "Reanuda la reproducción", "Continúa la música", "Continúa la reproducción",
            "Pon la siguiente canción.", "Salta esta canción", "Pausa TIDAL",
            "  ¡PAUSA   LA MÚSICA!  "
    })
    void explicitMediaControlsShouldBypassTheModel(String command) {
        SemanticRouter delegate = mock(SemanticRouter.class);

        assertEquals(Capability.MEDIA_CONTROL, new GuardedSemanticRouter(delegate).classify(command));
        verifyNoInteractions(delegate);
    }

    @ParameterizedTest
    @CsvSource({
            "Sube el volumen de TIDAL, AUDIO_CONTROL",
            "Silencia Spotify, AUDIO_CONTROL",
            "Abre TIDAL, OS_COMMAND",
            "Cierra Spotify, OS_COMMAND"
    })
    void mediaApplicationCommandsShouldRetainTheirOwnCapabilities(String command, Capability expected) {
        SemanticRouter delegate = mock(SemanticRouter.class);

        assertEquals(expected, new GuardedSemanticRouter(delegate).classify(command));
        verifyNoInteractions(delegate);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "¿Qué canción está sonando?", "Explícame cómo pausar la música",
            "No pauses la música", "Pon Comfortably Numb", "Activa shuffle"
    })
    void incompatibleMediaDecisionsShouldFallBackToGeneral(String command) {
        SemanticRouter delegate = mock(SemanticRouter.class);
        when(delegate.classify(command)).thenReturn(Capability.MEDIA_CONTROL);

        assertEquals(Capability.GENERAL, new GuardedSemanticRouter(delegate).classify(command));
        verify(delegate).classify(command);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Haz que la música siga sonando.", "pasa esta", "dale", "sigue", "vuelve", "pon otra",
            "Siguiente", "Anterior", "Continúa", "Vuelve atrás"
    })
    void inconclusiveShapesShouldNotOverrideASemanticMediaDecision(String command) {
        SemanticRouter delegate = mock(SemanticRouter.class);
        when(delegate.classify(command)).thenReturn(Capability.MEDIA_CONTROL);

        assertEquals(Capability.MEDIA_CONTROL, new GuardedSemanticRouter(delegate).classify(command));
        verify(delegate).classify(command);
        verifyNoMoreInteractions(delegate);
    }

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
}

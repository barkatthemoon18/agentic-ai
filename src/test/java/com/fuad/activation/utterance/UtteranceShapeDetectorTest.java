package com.fuad.activation.utterance;

import com.fuad.assistant.session.ConversationSnapshot;
import com.fuad.assistant.skills.os.OsConversationState;
import com.fuad.enums.Capability;
import com.fuad.enums.UtteranceDecision;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UtteranceShapeDetectorTest {
    private final UtteranceShapeDetector detector = new UtteranceShapeDetector();

    @Test
    void shouldRecognizeIndependentDatePersonAndAudioRequests() {
        assertDecision(UtteranceDecision.NEW_REQUEST,
                UtteranceClassificationRequest.withoutContext("¿En qué fecha estamos?"));
        assertDecision(UtteranceDecision.NEW_REQUEST,
                UtteranceClassificationRequest.withoutContext("¿Quién fue Alan Turing?"));
        assertDecision(UtteranceDecision.NEW_REQUEST,
                withContext("Silencia tu voz", "Explícame RSA", "RSA usa dos claves."));
        assertDecision(UtteranceDecision.NEW_REQUEST,
                withContext("¿En qué año murió Grace Hopper?", "Háblame de Ada Lovelace",
                        "Ada Lovelace fue una matemática británica."));
        assertDecision(UtteranceDecision.NEW_REQUEST,
                UtteranceClassificationRequest.withoutContext("Recomiéndame un libro de ciencia ficción"));
        assertDecision(UtteranceDecision.NEW_REQUEST,
                UtteranceClassificationRequest.withoutContext("Vuelve a responder"));
    }

    @Test
    void shouldRecognizeSourceRequestAsFollowUp() {
        assertDecision(UtteranceDecision.FOLLOW_UP,
                withContext("¿Puedes darme la fuente de eso?",
                        "¿Cuál es la versión estable?", "La versión estable es la 4.2."));
    }

    @Test
    void shouldRecognizeClearContextualRequestsOnlyWhenContextExists() {
        String[] utterances = {
                "Dame otro ejemplo",
                "¿Puedes profundizar en eso?",
                "¿Qué significa esa última parte?",
                "¿Y hay alternativas más seguras?",
                "No entendí, repítelo más despacio",
                "Hazlo más corto",
                "Ponme un ejemplo práctico",
                "Tradúcelo al portugués",
                "¿Cuánto cuesta ahora?"
        };

        for (String utterance : utterances) {
            assertDecision(UtteranceDecision.FOLLOW_UP,
                    withContext(utterance, "Explícame RSA", "RSA utiliza dos claves."));
            assertDecision(UtteranceDecision.OTHER,
                    UtteranceClassificationRequest.withoutContext(utterance));
        }
    }

    @Test
    void shouldRejectIncompatibleLifecycleReference() {
        assertDecision(UtteranceDecision.OTHER,
                withContext("¿Y cuándo murió?", "Explícame RSA", "RSA es criptografía asimétrica."));
        assertDecision(UtteranceDecision.FOLLOW_UP,
                withContext("¿Y cuándo murió?", "¿Quién fue Alan Turing?",
                        "Alan Turing fue un matemático británico."));
    }

    @Test
    void shouldRecognizePersonalFutureReflectionAsOther() {
        assertDecision(UtteranceDecision.OTHER,
                withContext("Algún día aprenderé criptografía",
                        "Explícame RSA", "RSA utiliza criptografía de clave pública."));
        assertDecision(UtteranceDecision.OTHER,
                withContext("Tal vez algún día use BSD",
                        "Compara Linux y BSD", "Ambos son sistemas tipo Unix."));
    }

    @Test
    void shouldRejectReportedSpeechBeforeInspectingEmbeddedRequest() {
        assertDecision(UtteranceDecision.OTHER,
                UtteranceClassificationRequest.withoutContext("Pedro preguntó qué fecha es"));
        assertDecision(UtteranceDecision.OTHER,
                UtteranceClassificationRequest.withoutContext("Mi hermana dijo abre Spotify"));
        assertDecision(UtteranceDecision.OTHER,
                UtteranceClassificationRequest.withoutContext("Juan Carlos Pérez preguntó qué fecha es"));
    }

    @Test
    void shouldRejectPersonalPastAndFutureEventsBeforeEmbeddedActions() {
        assertDecision(UtteranceDecision.OTHER,
                UtteranceClassificationRequest.withoutContext("Mañana voy a cerrar Spotify"));
        assertDecision(UtteranceDecision.OTHER,
                UtteranceClassificationRequest.withoutContext("Después voy a abrir Spotify"));
        assertDecision(UtteranceDecision.OTHER,
                UtteranceClassificationRequest.withoutContext("Ayer abrí Spotify"));
    }

    @Test
    void shouldLeaveAmbiguousUtterancesForTheModel() {
        Optional<UtteranceDecision> decision = detector.classify(
                UtteranceClassificationRequest.withoutContext("Está lloviendo afuera"));

        assertTrue(decision.isEmpty());
        assertTrue(detector.classify(
                UtteranceClassificationRequest.withoutContext("Ayer leí sobre Alan Turing")).isEmpty());
    }

    @Test
    void shouldRecognizeCatalogNavigationOnlyWithCatalogContext() {
        ConversationSnapshot catalog = new ConversationSnapshot(Capability.OS_COMMAND,
                "Qué aplicaciones tengo", "Encontré 25 aplicaciones", null, null, null,
                new OsConversationState(UUID.randomUUID()));

        assertDecision(UtteranceDecision.FOLLOW_UP,
                UtteranceClassificationRequest.withContext("Muéstrame más", catalog));
        assertDecision(UtteranceDecision.OTHER,
                UtteranceClassificationRequest.withoutContext("Muéstrame más"));
    }

    private UtteranceClassificationRequest withContext(String current, String previousUser,
                                                       String previousAssistant) {
        return UtteranceClassificationRequest.withContext(current,
                new ConversationSnapshot(Capability.GENERAL, previousUser, previousAssistant));
    }

    private void assertDecision(UtteranceDecision expected, UtteranceClassificationRequest request) {
        assertEquals(Optional.of(expected), detector.classify(request));
    }
}

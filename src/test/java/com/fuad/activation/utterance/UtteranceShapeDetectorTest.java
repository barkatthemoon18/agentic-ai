package com.fuad.activation.utterance;

import com.fuad.assistant.session.ConversationSnapshot;
import com.fuad.enums.Capability;
import com.fuad.enums.UtteranceDecision;
import org.junit.jupiter.api.Test;

import java.util.Optional;

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
                "No entendí, repítelo más despacio"
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
    }

    @Test
    void shouldLeaveAmbiguousUtterancesForTheModel() {
        Optional<UtteranceDecision> decision = detector.classify(
                UtteranceClassificationRequest.withoutContext("Está lloviendo afuera"));

        assertTrue(decision.isEmpty());
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

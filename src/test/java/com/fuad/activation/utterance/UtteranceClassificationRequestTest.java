package com.fuad.activation.utterance;

import com.fuad.assistant.session.ConversationSnapshot;
import com.fuad.enums.Capability;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UtteranceClassificationRequestTest {

    @Test
    void withoutContextShouldNormalizeTextAndExposeNoPreviousTurn() {
        UtteranceClassificationRequest request = UtteranceClassificationRequest.withoutContext("  Que hora es?  ");

        assertEquals("Que hora es?", request.getCurrentText());
        assertTrue(request.getPreviousTurn().isEmpty());
    }

    @Test
    void withContextShouldPreserveTheProvidedSnapshot() {
        ConversationSnapshot snapshot = new ConversationSnapshot(
                Capability.GENERAL, "Explicame RSA", "RSA es un algoritmo.");

        UtteranceClassificationRequest request =
                UtteranceClassificationRequest.withContext("Y para que sirve?", snapshot);

        assertSame(snapshot, request.getPreviousTurn().orElseThrow());
    }

    @Test
    void shouldRejectNullCurrentText() {
        assertThrows(NullPointerException.class,
                () -> UtteranceClassificationRequest.withoutContext(null));
    }

    @Test
    void shouldRejectBlankCurrentText() {
        assertThrows(IllegalArgumentException.class,
                () -> UtteranceClassificationRequest.withoutContext("   "));
    }

    @Test
    void shouldRejectNullContextContainer() {
        assertThrows(NullPointerException.class,
                () -> new UtteranceClassificationRequest("hola", null));
    }

    @Test
    void shouldRejectNullSnapshotWhenContextIsDeclared() {
        assertThrows(NullPointerException.class,
                () -> UtteranceClassificationRequest.withContext("hola", null));
    }
}

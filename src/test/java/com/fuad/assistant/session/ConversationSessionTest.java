package com.fuad.assistant.session;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class ConversationSessionTest {
    private final ConversationSnapshot initialSnapshot =
            new ConversationSnapshot("Explícame RSA", "RSA usa criptografía de clave pública.");

    @Test
    void shouldStartInactiveOpenAndClose() {
        ConversationSession session = new ConversationSession();

        assertFalse(session.isActive());
        assertFalse(session.hasExpired());

        session.openOrRefresh(initialSnapshot);
        assertTrue(session.isActive());
        assertFalse(session.hasExpired());
        assertSame(initialSnapshot, session.getSnapshot().orElseThrow());

        session.close();
        assertFalse(session.isActive());
        assertFalse(session.hasExpired());
        assertTrue(session.getSnapshot().isEmpty());
    }

    @Test
    void shouldRecognizeExpiredPreviouslyActiveSession() throws ReflectiveOperationException {
        ConversationSession session = new ConversationSession();
        session.openOrRefresh(initialSnapshot);
        Field activeUntil = ConversationSession.class.getDeclaredField("activeUntil");
        activeUntil.setAccessible(true);
        activeUntil.setLong(session, System.currentTimeMillis() - 1);

        assertFalse(session.isActive());
        assertTrue(session.hasExpired());
        assertTrue(session.getSnapshot().isEmpty());
    }

    @Test
    void openOrRefreshShouldReactivateExpiredSession() throws ReflectiveOperationException {
        ConversationSession session = new ConversationSession();
        session.openOrRefresh(initialSnapshot);
        Field activeUntil = ConversationSession.class.getDeclaredField("activeUntil");
        activeUntil.setAccessible(true);
        activeUntil.setLong(session, System.currentTimeMillis() - 1);

        ConversationSnapshot refreshedSnapshot =
                new ConversationSnapshot("¿Y para qué se usa?", "Se usa para proteger información.");
        session.openOrRefresh(refreshedSnapshot);

        assertTrue(session.isActive());
        assertFalse(session.hasExpired());
        assertSame(refreshedSnapshot, session.getSnapshot().orElseThrow());
    }

    @Test
    void openOrRefreshShouldRejectNullSnapshot() {
        ConversationSession session = new ConversationSession();

        assertThrows(NullPointerException.class, () -> session.openOrRefresh(null));
        assertFalse(session.isActive());
        assertTrue(session.getSnapshot().isEmpty());
    }
}

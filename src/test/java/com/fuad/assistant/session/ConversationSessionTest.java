package com.fuad.assistant.session;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class ConversationSessionTest {
    @Test
    void shouldStartInactiveActivateAndClose() {
        ConversationSession session = new ConversationSession();

        assertFalse(session.isActive());
        assertFalse(session.hasExpired());

        session.activate();
        assertTrue(session.isActive());
        assertFalse(session.hasExpired());

        session.close();
        assertFalse(session.isActive());
        assertFalse(session.hasExpired());
    }

    @Test
    void shouldRecognizeExpiredPreviouslyActiveSession() throws ReflectiveOperationException {
        ConversationSession session = new ConversationSession();
        Field activeUntil = ConversationSession.class.getDeclaredField("activeUntil");
        activeUntil.setAccessible(true);
        activeUntil.setLong(session, System.currentTimeMillis() - 1);

        assertFalse(session.isActive());
        assertTrue(session.hasExpired());
    }

    @Test
    void refreshShouldReactivateExpiredSession() throws ReflectiveOperationException {
        ConversationSession session = new ConversationSession();
        Field activeUntil = ConversationSession.class.getDeclaredField("activeUntil");
        activeUntil.setAccessible(true);
        activeUntil.setLong(session, System.currentTimeMillis() - 1);

        session.refresh();

        assertTrue(session.isActive());
        assertFalse(session.hasExpired());
    }
}

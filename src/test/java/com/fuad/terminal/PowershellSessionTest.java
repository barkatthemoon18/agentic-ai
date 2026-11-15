package com.fuad.terminal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PowershellSessionTest {

    @Test
    void initialStateShouldBeNotStartedAndNotRunning() {
        try (PowershellSession session = new PowershellSession()) {
            assertFalse(session.isRunning());
            assertTrue(session.process().isEmpty());
            assertEquals("NOT STARTED", session.error().orElse(null));
        }
        catch (Exception e) {
            fail("Closing fresh session should not throw: " + e.getMessage());
        }
    }

    @Test
    void closingBeforeStartShouldMarkClosedAndRemainSafe() throws Exception {
        PowershellSession session = new PowershellSession();
        session.close();

        assertFalse(session.isRunning());
        assertTrue(session.process().isEmpty());

        // Calling start after close should be a no-op
        session.start();
        assertFalse(session.isRunning());
        assertTrue(session.process().isEmpty());

        // Closing multiple times should be safe
        assertDoesNotThrow(session::close);
    }

    @Test
    void startOnWindowsShouldEitherSpawnOrSetDiagnosticError() throws Exception {
        try (PowershellSession session = new PowershellSession()) {
            session.start();

            // Calling start second time should be idempotent
            session.start();

            if (session.isRunning()) {
                assertTrue(session.process().isPresent());
                assertTrue(session.process().get().isAlive());
                assertTrue(session.error().isEmpty());
            }
            else {
                assertTrue(session.error().isPresent());
            }
        }
    }
}

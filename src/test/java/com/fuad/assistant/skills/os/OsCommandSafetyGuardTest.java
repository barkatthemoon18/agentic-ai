package com.fuad.assistant.skills.os;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OsCommandSafetyGuardTest {
    private final OsCommandSafetyGuard guard = new OsCommandSafetyGuard();

    @ParameterizedTest
    @ValueSource(strings = {
            "Abre Spotify",
            "Cierra Spotify",
            "¿Puedes cerrar Spotify?",
            "Quiero que cierres Spotify"
    })
    void shouldAllowImmediateCommands(String command) {
        assertTrue(guard.canExecute(command));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Mañana voy a cerrar Spotify",
            "Ayer abrí Spotify",
            "Después voy a cerrar Spotify",
            "Más tarde cerraré Spotify",
            "Luego cierro Spotify",
            "Spotify se está cerrando solo",
            "Spotify se cerró solo",
            "Spotify está cerrado",
            "Voy a abrir Spotify",
            "Vamos a cerrar Spotify",
            "Pienso abrir Spotify",
            "Creo que voy a cerrar Spotify"
    })
    void shouldRejectNonImmediateOrDescriptiveCommands(String command) {
        assertFalse(guard.canExecute(command));
    }
}

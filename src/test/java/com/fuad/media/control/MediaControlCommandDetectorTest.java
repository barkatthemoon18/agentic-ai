package com.fuad.media.control;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class MediaControlCommandDetectorTest {
    private final MediaControlCommandDetector detector = new MediaControlCommandDetector();

    @ParameterizedTest
    @CsvSource({
            "Siguiente canción., NEXT",
            "Siguiente pista, NEXT",
            "Pon la siguiente canción., NEXT",
            "Cambia a la siguiente pista, NEXT",
            "Pasa a la siguiente canción, NEXT",
            "Salta esta canción, NEXT",
            "Canción anterior, PREVIOUS",
            "Pon la pista anterior, PREVIOUS",
            "Vuelve a la canción anterior, PREVIOUS",
            "Retrocede a la pista anterior, PREVIOUS",
            "Pausa la música, PAUSE",
            "Pon en pausa la reproducción, PAUSE",
            "Pausa TIDAL, PAUSE",
            "Pausa Spotify, PAUSE",
            "Reanuda la reproducción, RESUME",
            "Continúa la música, RESUME",
            "Sigue reproduciendo la música, RESUME",
            "Reanuda TIDAL, RESUME",
            "'  ¡PAUSA   LA MÚSICA!  ', PAUSE",
            "'Por favor, pon la siguiente canción.', NEXT"
    })
    void shouldRecognizeCompletePlaybackCommands(String command, MediaAction action) {
        assertEquals(Optional.of(action), detector.detect(command));
        assertFalse(detector.definitelyNotMediaControl(command));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "No pauses la música", "No reanudes la reproducción", "¿Qué canción está sonando?",
            "Explícame cómo pausar la música", "Sube el volumen de TIDAL", "Cierra Spotify",
            "Activa shuffle", "Pon Comfortably Numb", "Pon \"Wish You Were Here\""
    })
    void shouldVetoOnlyCompleteExplicitContradictions(String command) {
        assertTrue(detector.definitelyNotMediaControl(command));
        assertTrue(detector.detect(command).isEmpty());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            " ", "Siguiente", "Siguiente.", "Anterior", "Continúa", "Vuelve atrás",
            "pasa esta", "dale", "sigue", "vuelve", "pon otra", "pon la siguiente",
            "pon la anterior", "Haz que la música siga sonando.", "pon esa de nuevo",
            "pon algo alegre", "No bajes el volumen, pausa la música",
            "Cierra Spotify y pausa TIDAL", "No pauses la música salvo que te lo pida",
            "Mi hermana dijo pausa la música", "Mañana voy a pausar TIDAL",
            "¿Qué canción está sonando y puedes pausarla?", "Pausa la música si estoy hablando"
    })
    void inconclusiveOrCompoundRequestsShouldBeNeitherRecognizedNorVetoed(String command) {
        assertTrue(detector.detect(command).isEmpty());
        assertFalse(detector.definitelyNotMediaControl(command));
    }
}

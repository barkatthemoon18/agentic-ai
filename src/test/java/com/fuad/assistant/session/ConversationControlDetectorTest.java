package com.fuad.assistant.session;

import com.fuad.enums.ConversationControl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConversationControlDetectorTest {
    private ConversationControlDetector detector;

    @BeforeEach
    void setUp() {
        detector = new ConversationControlDetector();
    }

    @ParameterizedTest(name = "detecta cierre explícito: {0}")
    @ValueSource(strings = {
            "cancela conversacion",
            "cancelar la conversación",
            "termina conversación",
            "terminar la conversacion",
            "cierra conversacion",
            "cerrar la conversación",
            "Ares cancela la conversación",
            "ares por favor termina la conversación",
            "ares cerrar conversacion por favor"
    })
    void shouldDetectExplicitCloseCommands(String text) {
        assertEquals(ConversationControl.CLOSE, detector.detect(text));
    }

    @ParameterizedTest(name = "detecta cierre natural: {0}")
    @ValueSource(strings = {
            "olvídalo",
            "déjalo",
            "nada olvídalo",
            "Ares olvídalo",
            "ares nada dejalo",
            "eso es todo",
            "ya está",
            "Ares eso es todo",
            "ares ya esta"
    })
    void shouldDetectNaturalCloseCommands(String text) {
        assertEquals(ConversationControl.CLOSE, detector.detect(text));
    }

    @Test
    void shouldNormalizeCaseAccentsPunctuationAndWhitespace() {
        String text = "  ¡ARES,   POR FAVOR CANCELA LA CONVERSACIÓN, POR FAVOR!  ";

        assertEquals(ConversationControl.CLOSE, detector.detect(text));
    }

    @ParameterizedTest(name = "tolera variación de STT: {0}")
    @ValueSource(strings = {
            "olbidalo",
            "olvidaloa",
            "olvidlo",
            "ares olbidalo",
            "nada olvidaloa"
    })
    void shouldDetectLikelySpeechToTextVariationsOfOlvidalo(String text) {
        assertEquals(ConversationControl.CLOSE, detector.detect(text));
    }

    @ParameterizedTest(name = "no interpreta como cierre: {0}")
    @ValueSource(strings = {
            "",
            "hola ares",
            "cancela",
            "conversacion",
            "quiero continuar la conversacion",
            "no cierres la conversacion",
            "olvida la compra de mañana",
            "eso no es todo",
            "ya casi esta"
    })
    void shouldNotDetectUnrelatedOrIncompletePhrases(String text) {
        assertEquals(ConversationControl.NONE, detector.detect(text));
    }
}

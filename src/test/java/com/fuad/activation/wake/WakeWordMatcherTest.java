package com.fuad.activation.wake;

import com.fuad.enums.WakeMatchStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WakeWordMatcherTest {
    private final WakeWordMatcher matcher = new WakeWordMatcher(List.of("Ares", "oye ares"), 0.85, 0.55);

    @Test
    void shouldRejectInvalidThresholds() {
        assertThrows(IllegalArgumentException.class, () -> new WakeWordMatcher(List.of("Ares"), 0.5, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new WakeWordMatcher(List.of("Ares"), 0.5, 0.7));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "...", "hola mundo"})
    void shouldReturnNoneWhenNoWakeWordIsPresent(String text) {
        WakeWordMatch result = matcher.match(text);

        assertEquals(WakeMatchStatus.NONE, result.status());
        assertEquals("", result.command());
    }

    @Test
    void shouldMatchWakeWordAndExtractCommandAfterPunctuation() {
        WakeWordMatch result = matcher.match("¡Oye Ares!, abre Spotify");

        assertEquals(WakeMatchStatus.MATCH, result.status());
        assertEquals("Oye Ares", result.candidate());
        assertEquals("abre Spotify", result.command());
        assertEquals(1.0, result.similarity());
    }

    @Test
    void shouldMatchIgnoringCase() {
        WakeWordMatch result = matcher.match("ARES qué hora es");

        assertEquals(WakeMatchStatus.MATCH, result.status());
        assertEquals("qué hora es", result.command());
    }

    @Test
    void shouldMarkSimilarCandidateAsAmbiguous() {
        WakeWordMatch result = matcher.match("Eres bastante rápido");

        assertEquals(WakeMatchStatus.AMBIGUOUS, result.status());
        assertEquals("Eres", result.candidate());
        assertEquals("bastante rápido", result.command());
        assertTrue(result.similarity() >= 0.55 && result.similarity() < 0.85);
    }
}

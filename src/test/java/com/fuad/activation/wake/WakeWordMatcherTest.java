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

        assertEquals(WakeMatchStatus.NONE, result.getStatus());
        assertEquals("", result.getCommand());
    }

    @Test
    void shouldMatchWakeWordAndExtractCommandAfterPunctuation() {
        WakeWordMatch result = matcher.match("¡Oye Ares!, abre Spotify");

        assertEquals(WakeMatchStatus.MATCH, result.getStatus());
        assertEquals("Oye Ares", result.getCandidate());
        assertEquals("abre Spotify", result.getCommand());
        assertEquals(1.0, result.getSimilarity());
    }

    @Test
    void shouldMatchIgnoringCase() {
        WakeWordMatch result = matcher.match("ARES qué hora es");

        assertEquals(WakeMatchStatus.MATCH, result.getStatus());
        assertEquals("qué hora es", result.getCommand());
    }

    @Test
    void shouldMarkSimilarCandidateAsAmbiguous() {
        WakeWordMatch result = matcher.match("Eres bastante rápido");

        assertEquals(WakeMatchStatus.AMBIGUOUS, result.getStatus());
        assertEquals("Eres", result.getCandidate());
        assertEquals("bastante rápido", result.getCommand());
        assertTrue(result.getSimilarity() >= 0.55 && result.getSimilarity() < 0.85);
    }
}

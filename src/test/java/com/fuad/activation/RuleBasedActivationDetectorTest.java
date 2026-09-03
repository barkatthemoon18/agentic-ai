package com.fuad.activation;

import com.fuad.activation.wake.WakeClassifier;
import com.fuad.activation.wake.WakeWordMatcher;
import com.fuad.enums.ActivationType;
import com.fuad.enums.WakeResolution;
import com.fuad.stt.TranscriptionResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleBasedActivationDetectorTest {
    private final WakeWordMatcher matcher = new WakeWordMatcher(List.of("Ares"), 0.85, 0.55);

    @Test
    void shouldIgnoreNullOrBlankTranscriptionWithoutCallingWakeClassifier() {
        AtomicBoolean called = new AtomicBoolean();
        RuleBasedActivationDetector detector = detector((candidate, command) -> {
            called.set(true);
            return WakeResolution.WAKE;
        });

        ActivationResult nullText = detector.detect(new TranscriptionResult(null, "es", 0));
        ActivationResult blankText = detector.detect(new TranscriptionResult("   ", "es", 0));

        assertFalse(nullText.isActivated());
        assertFalse(blankText.isActivated());
        assertFalse(called.get());
    }

    @Test
    void shouldPrioritizeExactWakeWordWithoutCallingWakeClassifier() {
        AtomicBoolean called = new AtomicBoolean();

        ActivationResult result = detector((candidate, command) -> {
            called.set(true);
            return WakeResolution.NONE;
        }).detect(transcription("Ares, abre Spotify"));

        assertTrue(result.isActivated());
        assertEquals(ActivationType.WAKE_WORD, result.getType());
        assertEquals("abre Spotify", result.getCommand());
        assertFalse(called.get());
    }

    @Test
    void shouldResolveAmbiguousWakeAsWakeWord() {
        ActivationResult result = detector((candidate, command) -> WakeResolution.WAKE)
                .detect(transcription("Eres abre Spotify"));

        assertTrue(result.isActivated());
        assertEquals(ActivationType.WAKE_WORD, result.getType());
        assertEquals("abre Spotify", result.getCommand());
    }

    @Test
    void shouldResolveAmbiguousWakeAsSemanticIntentUsingOriginalText() {
        ActivationResult result = detector((candidate, command) -> WakeResolution.SEMANTIC_INTENT)
                .detect(transcription("Eres abre Spotify"));

        assertTrue(result.isActivated());
        assertEquals(ActivationType.SEMANTIC_INTENT, result.getType());
        assertEquals("Eres abre Spotify", result.getCommand());
    }

    @Test
    void shouldActivateConfiguredIntentPhraseIgnoringCase() {
        ActivationResult result = detector((candidate, command) -> WakeResolution.NONE)
                .detect(transcription("NECESITO QUE revises esto"));

        assertTrue(result.isActivated());
        assertEquals(ActivationType.INTENT_PHRASE, result.getType());
    }

    @Test
    void shouldReturnNoneWhenNoExplicitRuleMatches() {
        ActivationResult result = detector((candidate, command) -> WakeResolution.NONE)
                .detect(transcription("Que hora es?"));

        assertFalse(result.isActivated());
        assertEquals(ActivationType.NONE, result.getType());
    }

    private RuleBasedActivationDetector detector(WakeClassifier wakeClassifier) {
        return new RuleBasedActivationDetector(matcher, wakeClassifier, List.of("necesito que"));
    }

    private TranscriptionResult transcription(String text) {
        return new TranscriptionResult(text, "es", 1.0);
    }
}

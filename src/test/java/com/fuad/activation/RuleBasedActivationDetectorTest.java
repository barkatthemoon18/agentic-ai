package com.fuad.activation;

import com.fuad.activation.semantic.SemanticActivationClassifier;
import com.fuad.activation.wake.WakeClassifier;
import com.fuad.activation.wake.WakeWordMatcher;
import com.fuad.enums.ActivationType;
import com.fuad.enums.WakeResolution;
import com.fuad.stt.TranscriptionResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class RuleBasedActivationDetectorTest {
    private final WakeWordMatcher matcher = new WakeWordMatcher(List.of("Ares"), 0.85, 0.55);

    @Test
    void shouldIgnoreNullOrBlankTranscriptionWithoutCallingClassifiers() {
        AtomicBoolean called = new AtomicBoolean();
        RuleBasedActivationDetector detector = detector(
                (candidate, command) -> { called.set(true); return WakeResolution.WAKE; },
                text -> { called.set(true); return true; });

        ActivationResult nullText = detector.detect(new TranscriptionResult(null, "es", 0));
        ActivationResult blankText = detector.detect(new TranscriptionResult("   ", "es", 0));

        assertFalse(nullText.isActivated());
        assertFalse(blankText.isActivated());
        assertFalse(called.get());
    }

    @Test
    void shouldPrioritizeExactWakeWord() {
        ActivationResult result = detector((candidate, command) -> WakeResolution.NONE, text -> false)
                .detect(transcription("Ares, abre Spotify"));

        assertTrue(result.isActivated());
        assertEquals(ActivationType.WAKE_WORD, result.getType());
        assertEquals("abre Spotify", result.getCommand());
    }

    @Test
    void shouldResolveAmbiguousWakeAsWakeWord() {
        ActivationResult result = detector((candidate, command) -> WakeResolution.WAKE, text -> false)
                .detect(transcription("Eres abre Spotify"));

        assertTrue(result.isActivated());
        assertEquals(ActivationType.WAKE_WORD, result.getType());
        assertEquals("abre Spotify", result.getCommand());
    }

    @Test
    void shouldResolveAmbiguousWakeAsSemanticIntentUsingOriginalText() {
        ActivationResult result = detector((candidate, command) -> WakeResolution.SEMANTIC_INTENT, text -> false)
                .detect(transcription("Eres abre Spotify"));

        assertEquals(ActivationType.SEMANTIC_INTENT, result.getType());
        assertEquals("Eres abre Spotify", result.getCommand());
    }

    @Test
    void shouldActivateConfiguredIntentPhraseIgnoringCase() {
        ActivationResult result = detector((candidate, command) -> WakeResolution.NONE, text -> false)
                .detect(transcription("NECESITO QUE revises esto"));

        assertTrue(result.isActivated());
        assertEquals(ActivationType.INTENT_PHRASE, result.getType());
    }

    @Test
    void shouldUseSemanticClassifierAsLastFallback() {
        ActivationResult activated = detector((candidate, command) -> WakeResolution.NONE, text -> true)
                .detect(transcription("¿Qué hora es?"));
        ActivationResult ignored = detector((candidate, command) -> WakeResolution.NONE, text -> false)
                .detect(transcription("Está lloviendo"));

        assertEquals(ActivationType.SEMANTIC_INTENT, activated.getType());
        assertFalse(ignored.isActivated());
        assertEquals(ActivationType.NONE, ignored.getType());
    }

    private RuleBasedActivationDetector detector(WakeClassifier wakeClassifier,
                                                  SemanticActivationClassifier semanticClassifier) {
        return new RuleBasedActivationDetector(matcher, wakeClassifier, semanticClassifier, List.of("necesito que"));
    }

    private TranscriptionResult transcription(String text) {
        return new TranscriptionResult(text, "es", 1.0);
    }
}

package com.fuad.activation;

import com.fuad.activation.wake.WakeClassifier;
import com.fuad.activation.wake.WakeWordMatch;
import com.fuad.activation.wake.WakeWordMatcher;
import com.fuad.vad.WakeMatchStatus;
import com.fuad.vad.WakeResolution;
import com.fuad.stt.TranscriptionResult;

import java.util.List;
import java.util.Locale;
import java.text.Normalizer;

public class RuleBasedActivationDetector implements ActivationDetector {
    private final WakeWordMatcher wakeWordMatcher;
    private final WakeClassifier wakeClassifier;
    private final List<String> intentPhrases;

    public RuleBasedActivationDetector(WakeWordMatcher wakeWordMatcher, WakeClassifier wakeClassifier,
                                       List<String> intentPhrases) {
        this.wakeWordMatcher = wakeWordMatcher;
        this.wakeClassifier = wakeClassifier;
        this.intentPhrases = List.copyOf(intentPhrases);
    }

    @Override
    public ActivationResult detect(TranscriptionResult transcriptionResult) {
        String original = transcriptionResult.text() != null ? transcriptionResult.text().trim() : "";
        if (original.isEmpty()) {
            return ActivationResult.none();
        }
        String normalized = original.toLowerCase(Locale.ROOT);
        WakeWordMatch wakeWordMatch = wakeWordMatcher.match(original);
        if (wakeWordMatch.status() == WakeMatchStatus.MATCH) {
            System.out.printf("WAKE -> MATCH | %.2f | candidate='%s'%n", wakeWordMatch.similarity(), wakeWordMatch.candidate());
            return new ActivationResult(true, ActivationType.WAKE_WORD, wakeWordMatch.command());
        }
        if (wakeWordMatch.status() == WakeMatchStatus.AMBIGUOUS) {
            System.out.printf("WAKE -> AMBIGUOUS | %.2f | candidate='%s'%n", wakeWordMatch.similarity(), wakeWordMatch.candidate());
            WakeResolution resolution = wakeClassifier.classify(wakeWordMatch.candidate(), wakeWordMatch.command());
            System.out.println("WAKE AI -> " + resolution);
            if (resolution == WakeResolution.WAKE) {
                if (looksLikeMisheardOpenCommand(wakeWordMatch)) {
                    System.out.println("WAKE -> REJECTED AMBIGUOUS ACTION");
                    return ActivationResult.none();
                }
                return new ActivationResult(true, ActivationType.WAKE_WORD, wakeWordMatch.command());
            }
            if (resolution == WakeResolution.SEMANTIC_INTENT) {
                return new ActivationResult(true, ActivationType.SEMANTIC_INTENT, original);
            }
        }
        for (String phrase : intentPhrases) {
            if (normalized.contains(phrase.toLowerCase(Locale.ROOT))) {
                System.out.println("ACTIVATION -> INTENT_PHRASE | phrase='" + phrase + "'");
                return new ActivationResult(true, ActivationType.INTENT_PHRASE, original);
            }
        }
        return ActivationResult.none();
    }

    private boolean looksLikeMisheardOpenCommand(WakeWordMatch match) {
        String candidate = normalize(match.candidate());
        String command = match.command().trim();
        return candidate.matches("avr?es?|abr?es?")
                && command.matches("(?U)[\\p{L}\\p{N}._-]+");
    }

    private String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }
}

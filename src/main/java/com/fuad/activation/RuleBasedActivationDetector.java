package com.fuad.activation;

import com.fuad.activation.wake.WakeClassifier;
import com.fuad.activation.wake.WakeWordMatch;
import com.fuad.activation.wake.WakeWordMatcher;
import com.fuad.enums.ActivationType;
import com.fuad.enums.WakeMatchStatus;
import com.fuad.enums.WakeResolution;
import com.fuad.stt.TranscriptionResult;

import java.util.List;
import java.util.Locale;

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
        String original = transcriptionResult.getText() != null ? transcriptionResult.getText().trim() : "";
        if (original.isEmpty()) {
            return ActivationResult.none();
        }
        String normalized = original.toLowerCase(Locale.ROOT);
        WakeWordMatch wakeWordMatch = wakeWordMatcher.match(original);
        if (wakeWordMatch.getStatus() == WakeMatchStatus.MATCH) {
            System.out.printf("WAKE -> MATCH | %.2f | candidate='%s'%n", wakeWordMatch.getSimilarity(), wakeWordMatch.getCandidate());
            return new ActivationResult(true, ActivationType.WAKE_WORD, wakeWordMatch.getCommand());
        }
        if (wakeWordMatch.getStatus() == WakeMatchStatus.AMBIGUOUS) {
            System.out.printf("WAKE -> AMBIGUOUS | %.2f | candidate='%s'%n", wakeWordMatch.getSimilarity(), wakeWordMatch.getCandidate());
            WakeResolution resolution = wakeClassifier.classify(wakeWordMatch.getCandidate(), wakeWordMatch.getCommand());
            System.out.println("WAKE AI -> " + resolution);
            if (resolution == WakeResolution.WAKE) {
                return new ActivationResult(true, ActivationType.WAKE_WORD, wakeWordMatch.getCommand());
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
}

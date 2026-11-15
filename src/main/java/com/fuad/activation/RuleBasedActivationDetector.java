package com.fuad.activation;

import com.fuad.activation.wake.WakeClassifier;
import com.fuad.activation.wake.WakeWordMatch;
import com.fuad.activation.wake.WakeWordMatcher;
import com.fuad.vad.WakeMatchStatus;
import com.fuad.vad.WakeResolution;
import com.fuad.stt.TranscriptionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;
import java.text.Normalizer;

import static com.fuad.logging.LogCategory.ACTIVATION;

public class RuleBasedActivationDetector implements ActivationDetector {
    private static final Logger LOGGER = LoggerFactory.getLogger(RuleBasedActivationDetector.class);

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
            LOGGER.debug(ACTIVATION, "WAKE -> MATCH | {} | candidate='{}'",
                    wakeWordMatch.similarity(), wakeWordMatch.candidate());
            return new ActivationResult(true, ActivationType.WAKE_WORD, wakeWordMatch.command());
        }
        if (wakeWordMatch.status() == WakeMatchStatus.AMBIGUOUS) {
            LOGGER.debug(ACTIVATION, "WAKE -> AMBIGUOUS | {} | candidate='{}'",
                    wakeWordMatch.similarity(), wakeWordMatch.candidate());
            WakeResolution resolution = wakeClassifier.classify(wakeWordMatch.candidate(), wakeWordMatch.command());
            LOGGER.debug(ACTIVATION, "WAKE AI -> {}", resolution);
            if (resolution == WakeResolution.WAKE) {
                if (looksLikeMisheardOpenCommand(wakeWordMatch)) {
                    LOGGER.debug(ACTIVATION, "WAKE -> REJECTED AMBIGUOUS ACTION");
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
                LOGGER.debug(ACTIVATION, "ACTIVATION -> INTENT_PHRASE | phrase='{}'", phrase);
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

package com.fuad.activation;

import com.fuad.enums.ActivationType;
import com.fuad.stt.TranscriptionResult;

import java.util.List;
import java.util.Locale;

public class RuleBasedActivationDetector implements ActivationDetector{
    private final List<String> wakeWords;
    private final List<String> intentPhrases;

    public  RuleBasedActivationDetector(List<String> wakeWords, List<String> intentPhrases) {
        this.wakeWords = wakeWords;
        this.intentPhrases = intentPhrases;
    }

    @Override
    public ActivationResult detect(TranscriptionResult transcriptionResult) {
        String original = transcriptionResult.getText().trim();
        String normalized = original.toLowerCase();

        for (String wakeWord : wakeWords) {
            String normalizedWakeWord = normalized.toLowerCase(Locale.ROOT);
            int index = normalized.indexOf(normalizedWakeWord);
            if (index >= 0) {
                String command = original.substring(index + wakeWord.length()).replaceFirst("^[,.:;!?¿¡\\s]+",
                        "").trim();
                return new ActivationResult(true, ActivationType.WAKE_WORD, command);
            }
        }
        for (String phrase : intentPhrases) {
            if (normalized.contains(phrase.toLowerCase(Locale.ROOT))) {
                return new ActivationResult(true, ActivationType.INTENT_PHRASE, original);
            }
        }
        return ActivationResult.none();
    }
}

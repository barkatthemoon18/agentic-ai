package com.fuad.activation;

import com.fuad.enums.ActivationType;
import com.fuad.stt.TranscriptionResult;

import java.util.List;

public class RuleBasedActivationDetector implements ActivationDetector{
    private final List<String> wakeWords;
    private final List<String> intentPhrases;

    public  RuleBasedActivationDetector(List<String> wakeWords, List<String> intentPhrases) {
        this.wakeWords = wakeWords;
        this.intentPhrases = intentPhrases;
    }

    @Override
    public ActivationResult detect(TranscriptionResult transcriptionResult) {
        String originalNormalized = transcriptionResult.getText().trim().toLowerCase();

        for (String wakeWord : wakeWords) {
            int index = originalNormalized.indexOf(wakeWord.toLowerCase());
            if (index >= 0) {
                String command = originalNormalized.substring(index + wakeWord.length())
                        .replaceFirst("^[,.:;\\s]+", "").trim();
                return new ActivationResult(true, ActivationType.WAKE_WORD, command);
            }
        }
        for (String phrase : intentPhrases) {
            if (originalNormalized.contains(phrase.toLowerCase())) {
                return new ActivationResult(true, ActivationType.INTENT_PHRASE, originalNormalized);
            }
        }
        return new ActivationResult(false, ActivationType.NONE, "");
    }
}

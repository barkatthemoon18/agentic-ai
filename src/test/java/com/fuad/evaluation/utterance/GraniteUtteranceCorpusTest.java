package com.fuad.evaluation.utterance;

import com.fuad.activation.utterance.GraniteUtteranceClassifier;
import com.fuad.enums.UtteranceDecision;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("model-evaluation")
class GraniteUtteranceCorpusTest {

    @Test
    void classifierShouldMeetCorpusThresholds() {
        String corpusName = System.getProperty("evaluation.corpus", "development");
        String resource = switch (corpusName) {
            case "development", "holdout" -> "evaluation/utterance-" + corpusName + ".jsonl";
            default -> throw new IllegalArgumentException(
                    "evaluation.corpus must be 'development' or 'holdout'");
        };
        List<UtteranceEvaluationCase> cases = new UtteranceCorpusLoader().loadResource(resource);
        OpenAIClient client = OpenAIOkHttpClient.builder()
                .baseUrl(System.getProperty("evaluation.base-url", "http://localhost:1234/v1"))
                .apiKey(System.getProperty("evaluation.api-key", "lm-studio"))
                .build();

        UtteranceEvaluationReport report = new UtteranceCorpusEvaluator()
                .evaluate(new GraniteUtteranceClassifier(client), cases);

        System.out.println(report.format());
        if (Boolean.getBoolean("evaluation.report-only")) {
            return;
        }
        double minimumMacroF1 = doubleProperty("evaluation.minimum-macro-f1", 0.90);
        double minimumRecall = doubleProperty("evaluation.minimum-recall", 0.85);
        double maximumOtherFalseActivation = doubleProperty(
                "evaluation.maximum-other-false-activation", 0.02);
        int minimumCases = integerProperty("evaluation.minimum-cases", 30);
        double criticalAccuracy = report.taggedAccuracy("critical");
        String diagnostics = report.format();

        assertAll(
                () -> assertTrue(cases.size() >= minimumCases,
                        "Corpus requires at least " + minimumCases + " cases"),
                () -> assertEquals(0, report.errorCount(), diagnostics),
                () -> assertEquals(0, report.followUpWithoutContextCount(), diagnostics),
                () -> assertTrue(report.macroF1() >= minimumMacroF1, diagnostics),
                () -> assertTrue(report.otherFalseActivationRate() <= maximumOtherFalseActivation, diagnostics),
                () -> assertTrue(!Double.isNaN(criticalAccuracy),
                        "Corpus requires at least one case tagged 'critical'"),
                () -> assertEquals(1.0, criticalAccuracy, 0.0001, diagnostics),
                () -> assertMinimumRecall(report, UtteranceDecision.NEW_REQUEST, minimumRecall, diagnostics),
                () -> assertMinimumRecall(report, UtteranceDecision.FOLLOW_UP, minimumRecall, diagnostics),
                () -> assertMinimumRecall(report, UtteranceDecision.OTHER, minimumRecall, diagnostics)
        );
    }

    private void assertMinimumRecall(UtteranceEvaluationReport report, UtteranceDecision decision,
                                     double minimumRecall, String diagnostics) {
        assertTrue(report.expectedCount(decision) > 0,
                "Corpus contains no expected cases for " + decision);
        assertTrue(report.recall(decision) >= minimumRecall, diagnostics);
    }

    private double doubleProperty(String name, double defaultValue) {
        return Double.parseDouble(System.getProperty(name, Double.toString(defaultValue)));
    }

    private int integerProperty(String name, int defaultValue) {
        return Integer.parseInt(System.getProperty(name, Integer.toString(defaultValue)));
    }
}

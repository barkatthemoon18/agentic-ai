package com.fuad.evaluation.classification;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecisionEvaluationReportTest {
    @Test
    void shouldCalculateIndependentBinaryMetricsAndConfusionMatrix() {
        List<DecisionCorpusCase> cases = List.of(
                testCase("a", "local"), testCase("b", "local"),
                testCase("c", "web"), testCase("d", "web"));
        DecisionEvaluationReport report = new DecisionCorpusEvaluator().evaluate(cases,
                List.of("local", "web"), testCase -> switch (testCase.id()) {
                    case "a" -> "local";
                    case "b", "c" -> "web";
                    default -> throw new IllegalStateException("offline");
                });

        assertEquals(.5, report.accuracy());
        assertEquals(1, report.errorCount());
        assertEquals(.5, report.recall("local"));
        assertEquals(.5, report.precision("web"));
        assertEquals(1L, report.confusionMatrix().get("local").get("web"));
        assertEquals(3, report.firstPassValidCount());
        assertEquals(0, report.retryCount());
        assertTrue(report.format("Decision report", "corpus=test").contains("macroF1="));
    }

    @Test
    void shouldReportRecoveredAndFailedRetriesSeparately() {
        List<DecisionCorpusCase> cases = List.of(testCase("a", "local"), testCase("b", "web"));
        DecisionEvaluationReport report = new DecisionCorpusEvaluator().evaluateDetailed(cases,
                List.of("local", "web"), testCase -> {
                    if (testCase.id().equals("a")) {
                        return new DecisionOutcome("local", 2);
                    }
                    throw new DecisionEvaluationFailure(2, new IllegalStateException("invalid retry"));
                });

        assertEquals(0, report.firstPassValidCount());
        assertEquals(2, report.retryCount());
        assertEquals(1, report.retryRecoveredCount());
        assertEquals(1.0, report.retryRate());
        assertEquals(1, report.errorCount());
    }

    private DecisionCorpusCase testCase(String id, String expected) {
        return new DecisionCorpusCase(id, "consulta " + id, expected, List.of(), "razón", null);
    }
}

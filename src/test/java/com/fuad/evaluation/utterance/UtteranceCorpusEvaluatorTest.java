package com.fuad.evaluation.utterance;

import com.fuad.activation.utterance.UtteranceClassifier;
import com.fuad.enums.UtteranceDecision;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UtteranceCorpusEvaluatorTest {

    @Test
    void shouldCalculateMetricsAndConfusionMatrix() {
        UtteranceEvaluationCase newRequest = evaluationCase(
                "new", "new", false, UtteranceDecision.NEW_REQUEST, "critical");
        UtteranceEvaluationCase followUp = evaluationCase(
                "follow", "follow", true, UtteranceDecision.FOLLOW_UP, "contextual");
        UtteranceEvaluationCase other = evaluationCase(
                "other", "other", false, UtteranceDecision.OTHER, "ambient");
        UtteranceClassifier classifier = request -> switch (request.getCurrentText()) {
            case "new" -> UtteranceDecision.NEW_REQUEST;
            case "follow" -> UtteranceDecision.OTHER;
            default -> UtteranceDecision.NEW_REQUEST;
        };

        UtteranceEvaluationReport report = new UtteranceCorpusEvaluator()
                .evaluate(classifier, List.of(newRequest, followUp, other));

        assertEquals(1, report.correctCount());
        assertEquals(0, report.errorCount());
        assertEquals(1.0 / 3.0, report.accuracy(), 0.0001);
        assertEquals(0.5, report.precision(UtteranceDecision.NEW_REQUEST), 0.0001);
        assertEquals(1.0, report.recall(UtteranceDecision.NEW_REQUEST), 0.0001);
        assertEquals(2.0 / 9.0, report.macroF1(), 0.0001);
        assertEquals(1.0, report.otherFalseActivationRate(), 0.0001);
        assertEquals(1.0, report.taggedAccuracy("critical"), 0.0001);
        assertEquals(1L, report.confusionMatrix()
                .get(UtteranceDecision.FOLLOW_UP).get(UtteranceDecision.OTHER));
        assertEquals(2, report.failures().size());
        assertTrue(report.format().contains("other expected=OTHER actual=NEW_REQUEST"));
    }

    @Test
    void shouldCaptureClassifierFailuresWithoutStoppingTheCorpus() {
        UtteranceEvaluationCase failing = evaluationCase(
                "failure", "failure", false, UtteranceDecision.OTHER, "critical");
        UtteranceEvaluationCase successful = evaluationCase(
                "success", "success", false, UtteranceDecision.NEW_REQUEST, "critical");
        UtteranceClassifier classifier = request -> {
            if (request.getCurrentText().equals("failure")) {
                throw new IllegalStateException("model unavailable");
            }
            return UtteranceDecision.NEW_REQUEST;
        };

        UtteranceEvaluationReport report = new UtteranceCorpusEvaluator()
                .evaluate(classifier, List.of(failing, successful));

        assertEquals(1, report.errorCount());
        assertEquals(1, report.correctCount());
        assertEquals(0.5, report.accuracy(), 0.0001);
        assertTrue(report.failures().getFirst().getError().contains("model unavailable"));
    }

    private UtteranceEvaluationCase evaluationCase(String id, String text, boolean contextAvailable,
                                                     UtteranceDecision expected, String tag) {
        UtteranceEvaluationCase evaluationCase = new UtteranceEvaluationCase();
        evaluationCase.setId(id);
        evaluationCase.setCurrentText(text);
        evaluationCase.setContextAvailable(contextAvailable);
        evaluationCase.setExpected(expected.name().toLowerCase());
        evaluationCase.setTags(List.of(tag));
        evaluationCase.setRationale("test");
        if (contextAvailable) {
            evaluationCase.setPreviousUserText("pregunta anterior");
            evaluationCase.setPreviousAssistantText("respuesta anterior");
            evaluationCase.setOwner("general");
        }
        return evaluationCase;
    }
}

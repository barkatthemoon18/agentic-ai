package com.fuad.evaluation.utterance;

import com.fuad.activation.utterance.UtteranceClassifier;
import com.fuad.enums.UtteranceDecision;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class UtteranceCorpusEvaluator {

    public UtteranceEvaluationReport evaluate(UtteranceClassifier classifier,
                                               List<UtteranceEvaluationCase> evaluationCases) {
        Objects.requireNonNull(classifier, "classifier cannot be null");
        Objects.requireNonNull(evaluationCases, "evaluationCases cannot be null");
        if (evaluationCases.isEmpty()) {
            throw new IllegalArgumentException("evaluationCases cannot be empty");
        }
        List<UtteranceEvaluationResult> results = new ArrayList<>();
        for (UtteranceEvaluationCase evaluationCase : evaluationCases) {
            long startedAt = System.nanoTime();
            try {
                UtteranceDecision actual = classifier.classify(evaluationCase.toClassificationRequest());
                results.add(UtteranceEvaluationResult.success(
                        evaluationCase, actual, System.nanoTime() - startedAt));
            } catch (RuntimeException exception) {
                results.add(UtteranceEvaluationResult.failure(
                        evaluationCase, exception, System.nanoTime() - startedAt));
            }
        }
        return new UtteranceEvaluationReport(results);
    }
}

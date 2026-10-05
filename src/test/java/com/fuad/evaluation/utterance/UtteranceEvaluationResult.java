package com.fuad.evaluation.utterance;

import com.fuad.enums.UtteranceDecision;

import java.util.Objects;

public record UtteranceEvaluationResult(
        UtteranceEvaluationCase evaluationCase,
        UtteranceDecision actual,
        String error,
        long latencyNanos) {

    public UtteranceEvaluationResult {
        evaluationCase = Objects.requireNonNull(evaluationCase, "evaluationCase cannot be null");
    }

    public static UtteranceEvaluationResult success(UtteranceEvaluationCase evaluationCase,
                                                     UtteranceDecision actual, long latencyNanos) {
        return new UtteranceEvaluationResult(evaluationCase,
                Objects.requireNonNull(actual, "actual cannot be null"), null, latencyNanos);
    }

    public static UtteranceEvaluationResult failure(UtteranceEvaluationCase evaluationCase,
                                                     RuntimeException exception, long latencyNanos) {
        Objects.requireNonNull(exception, "exception cannot be null");
        return new UtteranceEvaluationResult(evaluationCase, null,
                exception.getClass().getSimpleName() + ": " + exception.getMessage(), latencyNanos);
    }

    public boolean isValid() {
        return actual != null;
    }

    public boolean isCorrect() {
        return actual == evaluationCase.expectedDecision();
    }

    public double latencyMillis() {
        return latencyNanos / 1_000_000.0;
    }
}

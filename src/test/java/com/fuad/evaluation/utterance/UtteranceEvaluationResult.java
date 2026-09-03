package com.fuad.evaluation.utterance;

import com.fuad.enums.UtteranceDecision;
import lombok.Getter;

import java.util.Objects;

@Getter
public class UtteranceEvaluationResult {
    private final UtteranceEvaluationCase evaluationCase;
    private final UtteranceDecision actual;
    private final String error;
    private final long latencyNanos;

    private UtteranceEvaluationResult(UtteranceEvaluationCase evaluationCase, UtteranceDecision actual,
                                      String error, long latencyNanos) {
        this.evaluationCase = Objects.requireNonNull(evaluationCase, "evaluationCase cannot be null");
        this.actual = actual;
        this.error = error;
        this.latencyNanos = latencyNanos;
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

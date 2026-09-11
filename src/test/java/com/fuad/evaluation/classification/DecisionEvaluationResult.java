package com.fuad.evaluation.classification;

public record DecisionEvaluationResult(DecisionCorpusCase testCase, String actual,
                                       RuntimeException error, long latencyNanos,
                                       int attempts) {
    public DecisionEvaluationResult {
        if (attempts < 1) {
            throw new IllegalArgumentException("attempts must be positive");
        }
    }

    public boolean isValid() {
        return error == null && actual != null;
    }

    public boolean isCorrect() {
        return isValid() && testCase.normalizedExpected().equals(actual);
    }
}

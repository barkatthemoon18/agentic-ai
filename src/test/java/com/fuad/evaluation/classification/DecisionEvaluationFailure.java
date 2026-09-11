package com.fuad.evaluation.classification;

public final class DecisionEvaluationFailure extends RuntimeException {
    private final int attempts;

    public DecisionEvaluationFailure(int attempts, RuntimeException cause) {
        super(cause);
        if (attempts < 1) {
            throw new IllegalArgumentException("attempts must be positive");
        }
        this.attempts = attempts;
    }

    public int attempts() {
        return attempts;
    }
}

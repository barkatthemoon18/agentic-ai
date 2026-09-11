package com.fuad.evaluation.classification;

public record DecisionOutcome(String label, int attempts) {
    public DecisionOutcome {
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("label cannot be blank");
        }
        if (attempts < 1) {
            throw new IllegalArgumentException("attempts must be positive");
        }
    }
}

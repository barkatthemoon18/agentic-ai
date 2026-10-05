package com.fuad.activation.wake;

public record CandidateMatch(
        String candidate,
        int tokenCount,
        double similarity) {
}

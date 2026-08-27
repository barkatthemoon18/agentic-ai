package com.fuad.activation.wake;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class CandidateMatch {
    String candidate;
    int tokenCount;
    double similarity;
}

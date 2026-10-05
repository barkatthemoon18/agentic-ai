package com.fuad.vad;

public record VadResult(
        float probability,
        boolean speech) {
}

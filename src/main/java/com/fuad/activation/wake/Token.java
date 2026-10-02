package com.fuad.activation.wake;

public record Token(
        String value,
        int start,
        int end) {
}

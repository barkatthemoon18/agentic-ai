package com.fuad.model.runtime;

public record CommandResult(int exitCode, String output, boolean timedOut) {
    public CommandResult {
        output = output == null ? "" : output;
    }
}

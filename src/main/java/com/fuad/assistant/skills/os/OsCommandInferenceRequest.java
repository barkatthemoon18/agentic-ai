package com.fuad.assistant.skills.os;

record OsCommandInferenceRequest(String instructions, String command, boolean retry,
                                 String previousInvalidOutput, int maxCompletionTokens) {
    OsCommandInferenceRequest {
        if (instructions == null || instructions.isBlank()) {
            throw new IllegalArgumentException("instructions cannot be blank");
        }
        if (command == null || command.isBlank()) {
            throw new IllegalArgumentException("command cannot be blank");
        }
        if (maxCompletionTokens <= 0) {
            throw new IllegalArgumentException("maxCompletionTokens must be positive");
        }
        if (!retry && previousInvalidOutput != null) {
            throw new IllegalArgumentException("previousInvalidOutput requires retry=true");
        }
    }
}

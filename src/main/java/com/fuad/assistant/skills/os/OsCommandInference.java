package com.fuad.assistant.skills.os;

import java.util.Optional;

@FunctionalInterface
interface OsCommandInference {
    Optional<String> infer(OsCommandInferenceRequest request);
}

package com.fuad.assistant.skills.general;

import java.util.Optional;

@FunctionalInterface
public interface GeneralBackendInference {
    Optional<String> infer(GeneralBackendInferenceRequest request);
}

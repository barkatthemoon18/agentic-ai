package com.fuad.assistant.routing;

import com.fuad.enums.Capability;

public interface SemanticRouter {
    Capability classify(String command);
}

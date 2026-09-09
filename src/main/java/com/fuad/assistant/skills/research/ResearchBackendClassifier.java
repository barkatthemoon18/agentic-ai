package com.fuad.assistant.skills.research;

public interface ResearchBackendClassifier {
    ResearchBackend classify(String query, ResearchBackend inheritedBackend);
}

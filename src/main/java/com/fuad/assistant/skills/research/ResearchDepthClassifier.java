package com.fuad.assistant.skills.research;

import com.fuad.enums.ResearchDepth;

public interface ResearchDepthClassifier {
    ResearchDepth classify(String query);
}

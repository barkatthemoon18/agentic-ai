package com.fuad.assistant.skills.research;

@FunctionalInterface
public interface ResearchPlanClassifier {
    ResearchPlan classify(String query);
}

package com.fuad.view.workspace.web;

@FunctionalInterface
public interface ResearchLifecycleListener {
    void onResearchUpdated(ResearchWorkspaceSnapshot snapshot);

    static ResearchLifecycleListener noop() {
        return snapshot -> {
            /* Empty intentionally */
        };
    }
}

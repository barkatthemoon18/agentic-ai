package com.fuad.view.workspace.web;

import java.util.List;

public record ResearchWorkspaceSnapshot(
        ResearchState state,
        String provider,
        String query,
        String summary,
        List<String> findings,
        List<ResearchSource> sources,
        List<ResearchVisual> visuals,
        boolean ttsDelivered,
        String completedAt) {
    /* Empty intentionally */
}

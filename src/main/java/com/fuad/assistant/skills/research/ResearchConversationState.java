package com.fuad.assistant.skills.research;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

public final class ResearchConversationState {
    private final ResearchBackend activeBackend;
    private final Map<ResearchBackend, ResearchBranchState> branches;

    public ResearchConversationState(ResearchBackend activeBackend,
                                     Map<ResearchBackend, ResearchBranchState> branches) {
        this.activeBackend = activeBackend;
        EnumMap<ResearchBackend, ResearchBranchState> copy = new EnumMap<>(ResearchBackend.class);
        if (branches != null) {
            copy.putAll(branches);
        }
        this.branches = Map.copyOf(copy);
    }

    public static ResearchConversationState empty() {
        return new ResearchConversationState(null, Map.of());
    }

    public Optional<ResearchBackend> getActiveBackend() {
        return Optional.ofNullable(activeBackend);
    }

    public Optional<ResearchBranchState> getBranch(ResearchBackend backend) {
        return Optional.ofNullable(branches.get(backend));
    }

    public Map<ResearchBackend, ResearchBranchState> getBranches() {
        return branches;
    }

    public ResearchConversationState withBranch(ResearchBackend backend, ResearchBranchState branch) {
        EnumMap<ResearchBackend, ResearchBranchState> updated = new EnumMap<>(ResearchBackend.class);
        updated.putAll(branches);
        updated.put(backend, branch);
        return new ResearchConversationState(backend, updated);
    }
}

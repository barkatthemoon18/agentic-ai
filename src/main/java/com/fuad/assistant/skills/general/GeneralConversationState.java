package com.fuad.assistant.skills.general;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

public final class GeneralConversationState {
    private final GeneralBackend activeBackend;
    private final SelectionOrigin selectionOrigin;
    private final Map<GeneralBackend, GeneralBranchState> branches;

    public GeneralConversationState(GeneralBackend activeBackend, SelectionOrigin selectionOrigin,
                                    Map<GeneralBackend, GeneralBranchState> branches) {
        if ((activeBackend == null) != (selectionOrigin == null)) {
            throw new IllegalArgumentException("active backend and selection origin must be set together");
        }
        this.activeBackend = activeBackend;
        this.selectionOrigin = selectionOrigin;
        EnumMap<GeneralBackend, GeneralBranchState> copy = new EnumMap<>(GeneralBackend.class);
        if (branches != null) {
            copy.putAll(branches);
        }
        this.branches = Map.copyOf(copy);
    }

    public static GeneralConversationState empty() {
        return new GeneralConversationState(null, null, Map.of());
    }

    public Optional<GeneralBackend> getActiveBackend() {
        return Optional.ofNullable(activeBackend);
    }

    public Optional<SelectionOrigin> getSelectionOrigin() {
        return Optional.ofNullable(selectionOrigin);
    }

    public Optional<GeneralBranchState> getBranch(GeneralBackend backend) {
        return Optional.ofNullable(branches.get(backend));
    }

    public Map<GeneralBackend, GeneralBranchState> getBranches() {
        return branches;
    }

    public GeneralConversationState withActiveBranch(GeneralBackend backend, SelectionOrigin origin,
                                                     GeneralBranchState branch) {
        EnumMap<GeneralBackend, GeneralBranchState> updated = new EnumMap<>(GeneralBackend.class);
        updated.putAll(branches);
        updated.put(backend, branch);
        return new GeneralConversationState(backend, origin, updated);
    }
}

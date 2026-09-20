package com.fuad.view.workspace.system;

import java.util.List;

public record SystemWorkspaceSnapshot(
        SystemAudioSnapshot audioSnapshot,
        SystemNetworkSnapshot networkSnapshot,
        List<SystemDisplaySnapshot> displays,
        SystemPowerSnapshot powerSnapshot) {
    /* Empty intentionally */
}

package com.fuad.view.workspace.system;

import java.time.Duration;

public record SystemPowerSnapshot(
        String plan,
        String source,
        String host,
        String sessionState,
        Duration uptime) {
    /* Empty intentionally */
}

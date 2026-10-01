package com.fuad.view.workspace.tools;

public record QuickAction(
        String id,
        String symbol,
        String name,
        String detail,
        String voiceHint,
        QuickActionKind kind) {
    /* Empty intentionally */
}

package com.fuad.view.workspace.dev;

public record QuickAction(
        String id,
        String symbol,
        String name,
        String detail,
        String voiceHint,
        QuickActionKind kind) {
    /* Empty intentionally */
}

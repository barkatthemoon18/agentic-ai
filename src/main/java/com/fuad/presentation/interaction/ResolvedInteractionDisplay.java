package com.fuad.presentation.interaction;

import javafx.stage.Screen;

import java.util.Objects;

public record ResolvedInteractionDisplay(String id, Screen screen, String reason) {
    public ResolvedInteractionDisplay {
        Objects.requireNonNull(id);
        Objects.requireNonNull(screen);
        Objects.requireNonNull(reason);
    }
}

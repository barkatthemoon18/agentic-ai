package com.fuad.view.deck;

import java.util.List;

public record DeckCategory(
        String id,
        String label,
        String subtitle,
        List<DeckAction> actions) {
    /* Empty intentionally */
}
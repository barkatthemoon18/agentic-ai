package com.fuad.media.enrichment;

import java.util.List;
import java.util.OptionalDouble;

public record MediaEnrichmentSnapshot(
        OptionalDouble volume,
        String quality,
        List<MediaQueueItem> queue) {

    public MediaEnrichmentSnapshot {
        volume = volume == null ? OptionalDouble.empty() : volume;
        quality = quality == null ? "" : quality.trim();
        queue = queue == null ? List.of() : List.copyOf(queue);
    }

    public static MediaEnrichmentSnapshot unavailable() {
        return new MediaEnrichmentSnapshot(OptionalDouble.empty(), "", List.of());
    }

    public record MediaQueueItem(
            String title,
            String artist) {
        /* Empty intentionally */
    }
}

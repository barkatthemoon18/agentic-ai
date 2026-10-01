package com.fuad.media.enrichment;

import java.util.List;
import java.util.OptionalDouble;

public record MediaEnrichmentSnapshot(
        String quality,
        List<MediaQueueItem> queue) {

    public MediaEnrichmentSnapshot {
        quality = quality == null ? "" : quality.trim();
        queue = queue == null ? List.of() : List.copyOf(queue);
    }

    public static MediaEnrichmentSnapshot unavailable() {
        return new MediaEnrichmentSnapshot("", List.of());
    }

    public record MediaQueueItem(
            String title,
            List<String> artists) {
        public MediaQueueItem {
            title = title == null ? "" : title.trim();
            artists = artists == null ? List.of() : artists.stream().filter(artist ->
                    artist != null && !artist.isBlank()).map(String::trim).distinct().toList();
        }

        public String displayArtist() {
            return String.join(", ", artists);
        }
    }
}

package com.fuad.media.enrichment;

public interface MediaEnrichmentProvider extends AutoCloseable {
    MediaEnrichmentSnapshot current();

    static MediaEnrichmentProvider unavailable() {
        return MediaEnrichmentSnapshot::unavailable;
    }

    @Override
    default void close() {
        /* Awaiting */
    }
}

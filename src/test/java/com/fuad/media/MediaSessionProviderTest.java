package com.fuad.media;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MediaSessionProviderTest {
    @Test
    void unavailableProviderShouldRejectAllActionsAndExposeNoSession() {
        MediaSessionProvider provider = MediaSessionProvider.unavailable();

        assertEquals(MediaSessionSnapshot.unavailable(), provider.current());
        assertTrue(provider.playbackState("TIDAL").isEmpty());
        assertFalse(provider.playPause());
        assertFalse(provider.pause());
        assertFalse(provider.resume());
        assertFalse(provider.next());
        assertFalse(provider.previous());
        assertDoesNotThrow(provider::close);
    }
}

package com.fuad.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssistantAudioSnapshotTest {

    @Test
    void shouldCalculateGainFromVolume() {
        AssistantAudioSnapshot snapshot = new AssistantAudioSnapshot(40, false);

        assertEquals(40, snapshot.getVolume());
        assertFalse(snapshot.isMuted());
        assertEquals(0.4f, snapshot.getGain(), 0.0001f);
    }

    @Test
    void shouldReturnZeroGainWhenMuted() {
        AssistantAudioSnapshot snapshot = new AssistantAudioSnapshot(40, true);

        assertTrue(snapshot.isMuted());
        assertEquals(0.0f, snapshot.getGain(), 0.0001f);
    }

    @Test
    void shouldAcceptZeroVolumeWhenMuted() {
        AssistantAudioSnapshot snapshot = new AssistantAudioSnapshot(0, true);

        assertEquals(0, snapshot.getVolume());
        assertTrue(snapshot.isMuted());
        assertEquals(0.0f, snapshot.getGain(), 0.0001f);
    }

    @Test
    void shouldRejectZeroVolumeWhenNotMuted() {
        assertThrows(IllegalArgumentException.class, () -> new AssistantAudioSnapshot(0, false));
    }

    @Test
    void shouldRejectVolumeBelowZero() {
        assertThrows(IllegalArgumentException.class, () -> new AssistantAudioSnapshot(-1, true));
    }

    @Test
    void shouldRejectVolumeAboveOneHundred() {
        assertThrows(IllegalArgumentException.class, () -> new AssistantAudioSnapshot(101, false));
    }
}

package com.fuad.audio.output;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.*;

class AudioOutputSnapshotTest {
    @ParameterizedTest
    @ValueSource(doubles = {0, 0.25, 1})
    void volumeShouldRemainAFractionIncludingSilentAndFullVolume(double volume) {
        AudioOutputSnapshot snapshot = new AudioOutputSnapshot(true, "endpoint", "Speakers",
                OptionalDouble.of(volume), true);
        assertEquals(volume, snapshot.volume().orElseThrow());
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.01, 1.01, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY})
    void volumeShouldRejectValuesOutsideFractionRange(double volume) {
        assertThrows(IllegalArgumentException.class, () -> new AudioOutputSnapshot(true, "", "",
                OptionalDouble.of(volume), false));
    }

    @Test
    void unavailableShouldDifferFromAnAvailableSilentDevice() {
        AudioOutputSnapshot unavailable = AudioOutputSnapshot.unavailable();
        assertFalse(unavailable.available());
        assertTrue(unavailable.volume().isEmpty());
        AudioOutputSnapshot silent = new AudioOutputSnapshot(true, null, null, OptionalDouble.of(0), false);
        assertTrue(silent.available());
        assertEquals(0, silent.volume().orElseThrow());
        assertEquals("", silent.deviceName());
        assertEquals("", silent.endpointId());
        assertThrows(NullPointerException.class, () -> new AudioOutputSnapshot(false, "", "", null, false));
    }
}

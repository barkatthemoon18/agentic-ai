package com.fuad.presentation;

import com.fuad.audio.AssistantAudioSnapshot;
import com.fuad.enums.PresentationMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OutputPresentationPolicyTest {
    private final OutputPresentationPolicy policy = new OutputPresentationPolicy(20);

    @Test
    void shouldUseTextOnlyWhenMuted() {
        AssistantAudioSnapshot snapshot = new AssistantAudioSnapshot(40, true);

        assertEquals(PresentationMode.TEXT_ONLY, policy.resolve(snapshot));
    }

    @Test
    void shouldUseTextOnlyForZeroVolume() {
        AssistantAudioSnapshot snapshot = new AssistantAudioSnapshot(0, true);

        assertEquals(PresentationMode.TEXT_ONLY, policy.resolve(snapshot));
    }

    @ParameterizedTest
    @CsvSource({
            "1, AUDIO_AND_TEXT",
            "10, AUDIO_AND_TEXT",
            "19, AUDIO_AND_TEXT",
            "20, AUDIO_ONLY",
            "21, AUDIO_ONLY",
            "100, AUDIO_ONLY"
    })
    void shouldResolveModeAccordingToThreshold(int volume, PresentationMode expected) {
        AssistantAudioSnapshot snapshot = new AssistantAudioSnapshot(volume, false);

        assertEquals(expected, policy.resolve(snapshot));
    }

    @Test
    void shouldRejectNullSnapshot() {
        assertThrows(NullPointerException.class, () -> policy.resolve(null));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 101})
    void shouldRejectInvalidThreshold(int threshold) {
        assertThrows(IllegalArgumentException.class, () -> new OutputPresentationPolicy(threshold));
    }
}

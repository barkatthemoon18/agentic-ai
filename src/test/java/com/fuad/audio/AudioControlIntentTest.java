package com.fuad.audio;

import com.fuad.enums.AudioAction;
import com.fuad.enums.AudioScope;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AudioControlIntentTest {
    @Test
    void shouldEnforceValueContractForEveryAction() {
        assertDoesNotThrow(() -> new AudioControlIntent(
                AudioAction.SET_VOLUME, AudioScope.ASSISTANT, 0));
        assertDoesNotThrow(() -> new AudioControlIntent(
                AudioAction.INCREASE_VOLUME, AudioScope.ASSISTANT, null));

        assertThrows(IllegalArgumentException.class, () -> new AudioControlIntent(
                AudioAction.SET_VOLUME, AudioScope.ASSISTANT, null));
        assertThrows(IllegalArgumentException.class, () -> new AudioControlIntent(
                AudioAction.SET_VOLUME, AudioScope.ASSISTANT, 101));
        assertThrows(IllegalArgumentException.class, () -> new AudioControlIntent(
                AudioAction.DECREASE_VOLUME, AudioScope.ASSISTANT, 0));
        assertThrows(IllegalArgumentException.class, () -> new AudioControlIntent(
                AudioAction.MUTE, AudioScope.ASSISTANT, 10));
    }

    @Test
    void unsupportedFactoryShouldPreserveScope() {
        AudioControlIntent intent = AudioControlIntent.unsupported(AudioScope.APPLICATION);

        assertEquals(AudioAction.UNSUPPORTED, intent.getAudioAction());
        assertEquals(AudioScope.APPLICATION, intent.getAudioScope());
        assertEquals(null, intent.getValue());
    }
}

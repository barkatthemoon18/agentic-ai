package com.fuad.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssistantAudioControllerTest {
    @Test
    void zeroVolumeShouldMuteAndUnmuteShouldRestoreLastAudibleVolume() {
        AssistantAudioController controller = new AssistantAudioController();
        controller.setVolume(40);

        assertEquals(0, controller.setVolume(0));
        assertTrue(controller.isMuted());
        assertEquals(0.0f, controller.getGain());

        assertEquals(40, controller.unmute());
        assertFalse(controller.isMuted());
        assertEquals(0.4f, controller.getGain());
    }

    @Test
    void positiveAdjustmentShouldUnmuteAndNegativeStepsShouldBeRejected() {
        AssistantAudioController controller = new AssistantAudioController();
        controller.setVolume(40);
        controller.mute();

        assertEquals(50, controller.increaseVolume());
        assertFalse(controller.isMuted());
        assertThrows(IllegalArgumentException.class, () -> controller.increaseVolume(0));
        assertThrows(IllegalArgumentException.class, () -> controller.decreaseVolume(-1));
    }

    @Test
    void decreasingWhileMutedShouldPreserveMuteAndUpdateRestoredVolume() {
        AssistantAudioController controller = new AssistantAudioController();
        controller.setVolume(60);
        controller.mute();

        assertEquals(50, controller.decreaseVolume());
        assertTrue(controller.isMuted());
        assertEquals(50, controller.unmute());
        assertEquals(0.5f, controller.getGain());
    }

    @Test
    void operationsShouldClampWithoutIntegerOverflow() {
        AssistantAudioController controller = new AssistantAudioController();

        assertEquals(100, controller.increaseVolume(Integer.MAX_VALUE));
        assertEquals(0, controller.decreaseVolume(Integer.MAX_VALUE));
        assertTrue(controller.isMuted());
    }
}

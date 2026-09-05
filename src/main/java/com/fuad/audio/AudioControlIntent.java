package com.fuad.audio;

import com.fuad.enums.AudioAction;
import com.fuad.enums.AudioScope;
import lombok.Getter;

import java.util.Objects;

@Getter
public class AudioControlIntent {
    private final AudioAction audioAction;
    private final AudioScope audioScope;
    private final Integer value;

    public AudioControlIntent(AudioAction audioAction, AudioScope audioScope, Integer value) {
        this.audioAction = Objects.requireNonNull(audioAction, "audioAction");
        this.audioScope = Objects.requireNonNull(audioScope, "audioScope");
        validateValue(audioAction, value);
        this.value = value;
    }

    public static AudioControlIntent unsupported(AudioScope audioScope) {
        return new AudioControlIntent(AudioAction.UNSUPPORTED, audioScope, null);
    }

    private static void validateValue(AudioAction audioAction, Integer value) {
        switch (audioAction) {
            case SET_VOLUME -> {
                if (value == null || value < 0 || value > 100) {
                    throw new IllegalArgumentException("SET_VOLUME requires a value between 0 and 100");
                }
            }
            case INCREASE_VOLUME, DECREASE_VOLUME -> {
                if (value != null && (value < 1 || value > 100)) {
                    throw new IllegalArgumentException(
                            audioAction + " requires a positive value no greater than 100");
                }
            }
            case MUTE, UNMUTE, UNSUPPORTED -> {
                if (value != null) {
                    throw new IllegalArgumentException(audioAction + " does not accept a value");
                }
            }
        }
    }
}

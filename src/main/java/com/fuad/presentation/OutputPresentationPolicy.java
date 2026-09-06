package com.fuad.presentation;

import com.fuad.audio.AssistantAudioSnapshot;
import com.fuad.enums.PresentationMode;
import lombok.Getter;

import java.util.Objects;

@Getter
public class OutputPresentationPolicy {
    private final int textUiVolumeThreshold;

    public OutputPresentationPolicy(int textUiVolumeThreshold) {
        if (textUiVolumeThreshold < 1 || textUiVolumeThreshold > 100) {
            throw new IllegalArgumentException("Text UI volume threshold must be between 1 and 100");
        }
        this.textUiVolumeThreshold = textUiVolumeThreshold;
    }

    public PresentationMode resolve(AssistantAudioSnapshot audioSnapshot) {
        Objects.requireNonNull(audioSnapshot, "audioSnapshot");
        if (audioSnapshot.isMuted() || audioSnapshot.getVolume() == 0) {
            return PresentationMode.TEXT_ONLY;
        }
        if (audioSnapshot.getVolume() < textUiVolumeThreshold) {
            return PresentationMode.AUDIO_AND_TEXT;
        }
        return PresentationMode.AUDIO_ONLY;
    }
}

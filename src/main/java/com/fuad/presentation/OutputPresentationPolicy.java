package com.fuad.presentation;

import com.fuad.audio.AssistantAudioSnapshot;
import com.fuad.enums.PresentationMode;

import java.util.Objects;

public final class OutputPresentationPolicy {
    private final int textUiVolumeThreshold;

    public OutputPresentationPolicy(int textUiVolumeThreshold) {
        if (textUiVolumeThreshold < 1 || textUiVolumeThreshold > 100) {
            throw new IllegalArgumentException("Text UI volume threshold must be between 1 and 100");
        }
        this.textUiVolumeThreshold = textUiVolumeThreshold;
    }

    public PresentationMode resolve(AssistantAudioSnapshot audioSnapshot) {
        Objects.requireNonNull(audioSnapshot, "audioSnapshot");
        if (audioSnapshot.muted() || audioSnapshot.volume() == 0) {
            return PresentationMode.TEXT_ONLY;
        }
        if (audioSnapshot.volume() < textUiVolumeThreshold) {
            return PresentationMode.AUDIO_AND_TEXT;
        }
        return PresentationMode.AUDIO_ONLY;
    }

    public int textUiVolumeThreshold() {
        return textUiVolumeThreshold;
    }
}

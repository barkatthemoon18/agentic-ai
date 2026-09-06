package com.fuad.audio;

import lombok.Getter;

@Getter
public class AssistantAudioSnapshot {
    int volume;
    boolean muted;

    public AssistantAudioSnapshot(int volume, boolean muted) {
        if (volume < 0 || volume > 100) {
            throw new IllegalArgumentException("Volume must be between 0 and 100");
        }
        if (volume == 0 && !muted) {
            throw new IllegalArgumentException("Zero volume must imply muted state");
        }
        this.volume = volume;
        this.muted = muted;
    }

    public float getGain() {
        return muted ? 0.0f : volume / 100.0f;
    }
}

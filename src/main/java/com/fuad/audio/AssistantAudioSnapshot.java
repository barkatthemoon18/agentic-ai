package com.fuad.audio;

public record AssistantAudioSnapshot(
        int volume,
        boolean muted) {

    public AssistantAudioSnapshot {
        if (volume < 0 || volume > 100) {
            throw new IllegalArgumentException("Volume must be between 0 and 100");
        }
        if (volume == 0 && !muted) {
            throw new IllegalArgumentException("Zero volume must imply muted state");
        }
    }

    public float gain() {
        return muted ? 0.0f : volume / 100.0f;
    }
}

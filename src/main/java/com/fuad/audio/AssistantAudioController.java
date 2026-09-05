package com.fuad.audio;

public class AssistantAudioController {
    private static final int DEFAULT_STEP = 10;
    private int volume = 100;
    private int lastAudibleVolume = volume;
    private boolean muted = false;

    public synchronized int getVolume() {
        return volume;
    }

    public synchronized boolean isMuted() {
        return muted;
    }

    public synchronized int setVolume(int volume) {
        this.volume = Math.clamp(volume, 0, 100);
        if (this.volume == 0) {
            muted = true;
        }
        else {
            lastAudibleVolume = this.volume;
            muted = false;
        }
        return this.volume;
    }

    public synchronized int increaseVolume() {
        return increaseVolume(DEFAULT_STEP);
    }

    public synchronized int increaseVolume(int step) {
        requirePositiveStep(step);
        volume = Math.clamp(((long) volume + step), 0, 100);
        lastAudibleVolume = volume;
        muted = false;
        return volume;
    }

    public synchronized int decreaseVolume() {
        return decreaseVolume(DEFAULT_STEP);
    }

    public synchronized int decreaseVolume(int step) {
        requirePositiveStep(step);
        volume = Math.clamp(((long) volume - step), 0, 100);
        if (volume == 0) {
            muted = true;
        }
        else {
            lastAudibleVolume = volume;
        }
        return volume;
    }

    public synchronized void mute() {
        muted = true;
    }

    public synchronized int unmute() {
        if (volume == 0) {
            volume = lastAudibleVolume;
        }
        muted = false;
        return volume;
    }

    public synchronized float getGain() {
        return muted ? 0.0f : volume / 100.0f;
    }

    private void requirePositiveStep(int step) {
        if (step <= 0) {
            throw new IllegalArgumentException("Volume step must be positive");
        }
    }
}

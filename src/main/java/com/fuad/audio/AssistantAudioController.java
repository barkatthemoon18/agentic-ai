package com.fuad.audio;

public class AssistantAudioController {
    private static final int DEFAULT_STEP = 10;
    private int volume = 100;
    private boolean muted = false;

    public synchronized int getVolume() {
        return volume;
    }

    public synchronized boolean isMuted() {
        return muted;
    }

    public synchronized void setVolume(int volume) {
        this.volume = Math.clamp(volume, 0, 100);
        if (volume > 0) {
            muted = false;
        }
    }

    public synchronized int increaseVolume() {
        return increaseVolume(DEFAULT_STEP);
    }

    public synchronized int increaseVolume(int step) {
        volume = Math.clamp(((long) volume + step), 0, 100);
        if (volume > 0) {
            muted = false;
        }
        return volume;
    }

    public synchronized int decreaseVolume() {
        return decreaseVolume(DEFAULT_STEP);
    }

    public synchronized int decreaseVolume(int step) {
        volume = Math.clamp(((long) volume - step), 0, 100);
        return volume;
    }

    public synchronized void mute() {
        muted = true;
    }

    public synchronized void unmute() {
        muted = false;
    }

    public synchronized float getGain() {
        return muted ? 0.0f : volume / 100.0f;
    }
}

package com.fuad.pipeline;

import com.fuad.audio.AudioDeviceInfo;
import com.fuad.audio.AudioPlaybackService;
import com.fuad.enums.AudioState;
import com.fuad.tts.TtsAudio;
import com.fuad.tts.TtsEngine;
import lombok.Getter;

public class AudioPipeline {
    private static final long POST_PLAYBACK_GUARD_NANOS = 350_000_000L;
    private final TtsEngine ttsEngine;
    private final AudioPlaybackService playbackService;
    private final AudioDeviceInfo outputDevice;
    @Getter
    private volatile AudioState state = AudioState.LISTENING;
    private volatile long listeningBlockedUntilNanos = 0;

    public AudioPipeline(TtsEngine ttsEngine, AudioPlaybackService playbackService, AudioDeviceInfo outputDevice) {
        this.ttsEngine = ttsEngine;
        this.playbackService = playbackService;
        this.outputDevice = outputDevice;
    }

    public synchronized boolean beginProcessing() {
        if (!canListen()) {
            return false;
        }
        state = AudioState.PROCESSING;
        System.out.println("AUDIO STATE -> PROCESSING");
        return true;
    }

    public void speak(String text) {
        boolean playbackStarted = false;

        try {
            TtsAudio audio = ttsEngine.synthesize(text);
            state = AudioState.SPEAKING;
            playbackStarted = true;
            System.out.println("AUDIO STATE -> SPEAKING");
            playbackService.play(outputDevice, audio);
        }
        finally {
            if (playbackStarted) {
                listeningBlockedUntilNanos = System.nanoTime() + POST_PLAYBACK_GUARD_NANOS;
            }
            state = AudioState.LISTENING;
            System.out.println("AUDIO STATE -> LISTENING (guard 350 ms)");
        }
    }

    public synchronized void finishProcessing() {
        if (state == AudioState.PROCESSING) {
            state = AudioState.LISTENING;
            System.out.println("AUDIO STATE -> LISTENING");
        }
    }

    public boolean canListen() {
        return state == AudioState.LISTENING && System.nanoTime() >= listeningBlockedUntilNanos;
    }

    public boolean isProcessing() {
        return state == AudioState.PROCESSING;
    }

    public boolean isSpeaking() {
        return state == AudioState.SPEAKING;
    }
}

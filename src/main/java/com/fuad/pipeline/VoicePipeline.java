package com.fuad.pipeline;

import com.fuad.audio.AudioFrame;
import com.fuad.enums.VoiceState;
import com.fuad.speech.SpeechBuffer;
import com.fuad.speech.SpeechSegment;
import com.fuad.speech.SpeechSegmentListener;
import com.fuad.vad.VadEngine;
import com.fuad.vad.VadResult;
import java.util.ArrayDeque;
import java.util.Deque;

public class VoicePipeline {
    private static final int SPEECH_START_FRAMES = 2;
    private static final int SILENCE_END_FRAMES = 20;
    private static final int PRE_ROLL_FRAMES = 10;
    private final Deque<AudioFrame> preRoll = new ArrayDeque<>();
    private final VadEngine vadEngine;
    private final SpeechBuffer speechBuffer;
    private final SpeechSegmentListener segmentListener;
    private final AudioPipeline audioPipeline;
    private VoiceState state = VoiceState.IDLE;
    private boolean audioWasBlocked = false;
    private int speechFrames = 0;
    private int silenceFrames = 0;

    public VoicePipeline(VadEngine vadEngine, SpeechBuffer speechBuffer,  SpeechSegmentListener segmentListener,
                         AudioPipeline audioPipeline) {
        this.vadEngine = vadEngine;
        this.speechBuffer = speechBuffer;
        this.segmentListener = segmentListener;
        this.audioPipeline = audioPipeline;
    }

    public void process(AudioFrame frame) {
        if (!audioPipeline.canListen()) {
            audioWasBlocked = true;
            return;
        }
        /* First sentence after SPEAKING + post-playback guard. Clear any other state */
        if (audioWasBlocked) {
            reset();
            audioWasBlocked = false;
            System.out.println("AUDIO INPUT -> READY");
        }
        VadResult result = vadEngine.process(frame);
        switch (state) {
            case IDLE -> processIdle(frame, result);
            case SPEAKING -> processSpeaking(frame, result);
        }
    }

    private void processIdle(AudioFrame frame, VadResult result) {
        updatePreRoll(frame);
        if (result.isSpeech()) {
            speechFrames++;
            if (speechFrames >= SPEECH_START_FRAMES) {
                startSpeech();
            }
        }
        else {
            speechFrames = 0;
        }
    }

    private void startSpeech() {
        state = VoiceState.SPEAKING;

        silenceFrames = 0;
        speechFrames = 0;
        speechBuffer.clear();
        for (AudioFrame frame : preRoll) {
            speechBuffer.add(frame);
        }
        preRoll.clear();
        System.out.println(">>> SPEECH START");
    }

    private void processSpeaking(AudioFrame frame, VadResult result) {
        speechBuffer.add(frame);
        if (result.isSpeech()) {
            silenceFrames = 0;
        }
        else {
            silenceFrames++;
            if (silenceFrames >= SILENCE_END_FRAMES) {
                endSpeech();
            }
        }
    }

    private void endSpeech() {
        SpeechSegment segment = speechBuffer.toSegment();
        System.out.println("<<< SPEECH END | " + speechBuffer.frameCount() + " frames | " + speechBuffer.durationMillis() + " ms");
        state = VoiceState.IDLE;
        silenceFrames = 0;
        speechFrames = 0;
        speechBuffer.clear();
        segmentListener.onSpeechSegment(segment);
    }

    private void updatePreRoll(AudioFrame frame) {
        preRoll.addLast(frame);

        while (preRoll.size() > PRE_ROLL_FRAMES) {
            preRoll.removeFirst();
        }
    }

    private void reset() {
        vadEngine.reset();
        speechBuffer.clear();
        preRoll.clear();
        state = VoiceState.IDLE;
        speechFrames = 0;
        silenceFrames = 0;
    }
}

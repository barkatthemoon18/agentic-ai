package com.fuad.speech;

import com.fuad.audio.AudioFrame;

import java.util.ArrayList;
import java.util.List;

public class SpeechBuffer {
    private final List<AudioFrame> frames = new ArrayList<>();

    public void add(AudioFrame frame) {
        frames.add(frame);
    }

    public void clear() {
        frames.clear();
    }

    public boolean isEmpty() {
        return frames.isEmpty();
    }

    public int frameCount() {
        return frames.size();
    }

    public long durationMillis() {
        if (frames.isEmpty()) {
            return 0;
        }
        return Math.round(frames.stream().mapToDouble(AudioFrame::durationMillis).sum());
    }

    public SpeechSegment toSegment() {
        if (frames.isEmpty()) {
            throw new IllegalStateException("Cannot create SpeechSegment from empty buffer");
        }
        int totalSamples = frames.stream().mapToInt(frame -> frame.getSamples().length).sum();
        float[] samples = new float[totalSamples];
        int offset = 0;
        for (AudioFrame frame : frames) {
            float[] frameSamples = frame.getSamples();
            System.arraycopy(frameSamples, 0, samples, offset, frameSamples.length);
            offset += frameSamples.length;
        }
        AudioFrame firstFrame = frames.getFirst();
        return new SpeechSegment(samples, firstFrame.getSampleRate(), firstFrame.getTimestampNanos());
    }
}

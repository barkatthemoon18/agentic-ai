package com.fuad.pipeline;

import com.fuad.audio.AudioFrame;
import com.fuad.audio.AudioPlaybackService;
import com.fuad.audio.AssistantAudioController;
import com.fuad.speech.SpeechBuffer;
import com.fuad.speech.SpeechSegment;
import com.fuad.tts.TtsAudio;
import com.fuad.tts.TtsEngine;
import com.fuad.vad.VadEngine;
import com.fuad.vad.VadResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VoicePipelineTest {
    @Test
    void shouldEmitSegmentAfterTwoSpeechAndTwentySilenceFrames() {
        StubVad vad = new StubVad();
        List<SpeechSegment> emitted = new ArrayList<>();
        VoicePipeline pipeline = new VoicePipeline(vad, new SpeechBuffer(), emitted::add, listeningAudioPipeline());

        for (int i = 0; i < 3; i++) process(pipeline, vad, false, i);
        process(pipeline, vad, true, 3);
        process(pipeline, vad, true, 4);
        for (int i = 5; i < 25; i++) process(pipeline, vad, false, i);

        assertEquals(1, emitted.size());
        SpeechSegment segment = emitted.getFirst();
        assertEquals(25, segment.getSamplesCount());
        assertEquals(0L, segment.getStartTimestampNanos());
        assertEquals(0f, segment.getSamples()[0]);
        assertEquals(24f, segment.getSamples()[24]);
    }

    @Test
    void shouldKeepOnlyTenPreRollFrames() {
        StubVad vad = new StubVad();
        List<SpeechSegment> emitted = new ArrayList<>();
        VoicePipeline pipeline = new VoicePipeline(vad, new SpeechBuffer(), emitted::add, listeningAudioPipeline());

        for (int i = 0; i < 15; i++) process(pipeline, vad, false, i);
        process(pipeline, vad, true, 15);
        process(pipeline, vad, true, 16);
        for (int i = 17; i < 37; i++) process(pipeline, vad, false, i);

        SpeechSegment segment = emitted.getFirst();
        assertEquals(30, segment.getSamplesCount());
        assertEquals(7f, segment.getSamples()[0]);
        assertEquals(7L, segment.getStartTimestampNanos());
    }

    @Test
    void shouldIgnoreFramesWhileAudioIsBlockedAndResetBeforeResuming() {
        StubVad vad = new StubVad();
        AudioPipeline audio = listeningAudioPipeline();
        VoicePipeline pipeline = new VoicePipeline(vad, new SpeechBuffer(), segment -> fail("must not emit"), audio);
        assertTrue(audio.beginProcessing());

        pipeline.process(frame(1));
        assertEquals(0, vad.processCount);

        audio.finishProcessing();
        process(pipeline, vad, false, 2);

        assertEquals(1, vad.resetCount);
        assertEquals(1, vad.processCount);
    }

    private void process(VoicePipeline pipeline, StubVad vad, boolean speech, long value) {
        vad.results.addLast(new VadResult(speech ? 0.9f : 0.1f, speech));
        pipeline.process(frame(value));
    }

    private AudioFrame frame(long value) {
        return new AudioFrame(new float[]{value}, 1_000, value);
    }

    private AudioPipeline listeningAudioPipeline() {
        TtsEngine tts = new TtsEngine() {
            @Override public TtsAudio synthesize(String text) { return new TtsAudio(new float[0], 16_000); }
            @Override public void close() { }
        };
        return new AudioPipeline(tts, new AudioPlaybackService(), null, new AssistantAudioController());
    }

    private static final class StubVad implements VadEngine {
        private final Deque<VadResult> results = new ArrayDeque<>();
        private int processCount;
        private int resetCount;

        @Override public VadResult process(AudioFrame frame) { processCount++; return results.removeFirst(); }
        @Override public void reset() { resetCount++; }
        @Override public void close() { }
    }
}

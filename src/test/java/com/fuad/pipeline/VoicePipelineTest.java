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
    void mutingDuringSpeechShouldDiscardBufferedAudioAndResetOnceUntilUnmuted() {
        StubVad vad = new StubVad();
        VoiceInputController input = new VoiceInputController();
        List<SpeechSegment> emitted = new ArrayList<>();
        List<AssistantActivityState> states = new ArrayList<>();
        List<VoiceSignalSnapshot> signals = new ArrayList<>();
        VoicePipeline pipeline = new VoicePipeline(vad, new SpeechBuffer(), emitted::add,
                listeningAudioPipeline(), states::add, signals::add, input);
        process(pipeline, vad, true, 0);
        process(pipeline, vad, true, 1);
        assertEquals(List.of(AssistantActivityState.LISTENING), states);
        input.setMuted(true);
        pipeline.process(frame(2));
        pipeline.process(frame(3));
        assertEquals(2, vad.processCount);
        assertEquals(1, vad.resetCount);
        assertEquals(List.of(AssistantActivityState.LISTENING, AssistantActivityState.IDLE), states);
        assertSame(VoiceSignalSnapshot.silence(), signals.getLast());

        input.setMuted(false);
        process(pipeline, vad, true, 100);
        process(pipeline, vad, true, 101);
        for (int i = 102; i < 122; i++) process(pipeline, vad, false, i);
        assertEquals(2, vad.resetCount);
        assertEquals(1, emitted.size());
        assertEquals(100f, emitted.getFirst().samples()[0]);
        assertEquals(22, emitted.getFirst().sampleCount());
        assertEquals(AssistantActivityState.IDLE, states.getLast());
    }

    @Test
    void mutingInputMustNotResetActivityOwnedByProcessing() {
        StubVad vad = new StubVad();
        VoiceInputController input = new VoiceInputController();
        AudioPipeline audio = listeningAudioPipeline();
        List<AssistantActivityState> states = new ArrayList<>();
        VoicePipeline pipeline = new VoicePipeline(vad, new SpeechBuffer(), ignored -> fail("no segment"),
                audio, states::add, VoiceSignalListener.noop(), input);
        process(pipeline, vad, true, 0);
        process(pipeline, vad, true, 1);
        assertTrue(audio.beginProcessing());
        input.setMuted(true);
        pipeline.process(frame(2));
        assertEquals(List.of(AssistantActivityState.LISTENING), states);
        assertTrue(audio.isProcessing());
    }

    @Test
    void framesShouldPublishRmsPeakAndVadProbabilityWithDefensiveSamples() {
        StubVad vad = new StubVad();
        List<VoiceSignalSnapshot> signals = new ArrayList<>();
        VoicePipeline pipeline = new VoicePipeline(vad, new SpeechBuffer(), ignored -> fail("no segment"),
                listeningAudioPipeline(), AssistantActivityListener.noop(), signals::add, new VoiceInputController());
        float[] samples = {0.3f, -0.4f};
        vad.results.add(new VadResult(0.7f, false));
        pipeline.process(new AudioFrame(samples, 16_000, 0));
        samples[0] = 1;
        VoiceSignalSnapshot signal = signals.getFirst();
        assertEquals(Math.sqrt(0.125), signal.rms(), 1e-7);
        assertEquals(0.4, signal.peak(), 1e-7);
        assertEquals(0.7, signal.vadProbability(), 1e-7);
        assertEquals(2, signal.sampleCount());
        assertEquals(0.3f, signal.sampleAt(0));
        assertEquals(-0.4f, signal.sampleAt(1));
    }

    @Test
    void submittingSpeechMustNotPublishIdleOverProcessingStartedBySegmentListener() {
        StubVad vad = new StubVad();
        AudioPipeline audio = listeningAudioPipeline();
        List<AssistantActivityState> states = new ArrayList<>();
        VoicePipeline pipeline = new VoicePipeline(vad, new SpeechBuffer(), ignored -> assertTrue(audio.beginProcessing()),
                audio, states::add);
        process(pipeline, vad, true, 0);
        process(pipeline, vad, true, 1);
        for (int i = 2; i < 22; i++) process(pipeline, vad, false, i);
        assertTrue(audio.isProcessing());
        assertEquals(List.of(AssistantActivityState.LISTENING), states);
    }

    @Test
    void shouldEmitSegmentAfterTwoSpeechAndTwentySilenceFrames() {
        StubVad vad = new StubVad();
        List<SpeechSegment> emitted = new ArrayList<>();
        VoicePipeline pipeline = new VoicePipeline(vad, new SpeechBuffer(), emitted::add, listeningAudioPipeline(), AssistantActivityListener.noop());

        for (int i = 0; i < 3; i++) process(pipeline, vad, false, i);
        process(pipeline, vad, true, 3);
        process(pipeline, vad, true, 4);
        for (int i = 5; i < 25; i++) process(pipeline, vad, false, i);

        assertEquals(1, emitted.size());
        SpeechSegment segment = emitted.getFirst();
        assertEquals(25, segment.sampleCount());
        assertEquals(0L, segment.startTimestampNanos());
        assertEquals(0f, segment.samples()[0]);
        assertEquals(24f, segment.samples()[24]);
    }

    @Test
    void shouldKeepOnlyTenPreRollFrames() {
        StubVad vad = new StubVad();
        List<SpeechSegment> emitted = new ArrayList<>();
        VoicePipeline pipeline = new VoicePipeline(vad, new SpeechBuffer(), emitted::add, listeningAudioPipeline(), AssistantActivityListener.noop());

        for (int i = 0; i < 15; i++) process(pipeline, vad, false, i);
        process(pipeline, vad, true, 15);
        process(pipeline, vad, true, 16);
        for (int i = 17; i < 37; i++) process(pipeline, vad, false, i);

        SpeechSegment segment = emitted.getFirst();
        assertEquals(30, segment.sampleCount());
        assertEquals(7f, segment.samples()[0]);
        assertEquals(7L, segment.startTimestampNanos());
    }

    @Test
    void shouldIgnoreFramesWhileAudioIsBlockedAndResetBeforeResuming() {
        StubVad vad = new StubVad();
        AudioPipeline audio = listeningAudioPipeline();
        VoicePipeline pipeline = new VoicePipeline(vad, new SpeechBuffer(), segment -> fail("must not emit"), audio, AssistantActivityListener.noop());
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

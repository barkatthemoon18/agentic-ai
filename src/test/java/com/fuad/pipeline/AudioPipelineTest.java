package com.fuad.pipeline;

import com.fuad.audio.AudioDeviceInfo;
import com.fuad.audio.AudioPlaybackService;
import com.fuad.audio.AssistantAudioController;
import com.fuad.audio.PlaybackSignalListener;
import com.fuad.enums.AudioState;
import com.fuad.tts.TtsAudio;
import com.fuad.tts.TtsEngine;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class AudioPipelineTest {
    @Test
    void playbackShouldPublishActivityAndSignalThenReturnToSilence() {
        TrackingPlayback playback = new TrackingPlayback();
        List<AssistantActivityState> activity = new ArrayList<>();
        List<VoiceSignalSnapshot> signals = new ArrayList<>();
        AudioPipeline pipeline = new AudioPipeline(tts(text -> new TtsAudio(new float[]{-0.5f}, 16_000)),
                playback, null, new AssistantAudioController(), activity::add, signals::add);
        assertTrue(pipeline.beginProcessing());

        pipeline.speak("hola");
        pipeline.finishProcessing();

        assertEquals(List.of(AssistantActivityState.PROCESSING, AssistantActivityState.SPEAKING,
                AssistantActivityState.IDLE), activity);
        VoiceSignalSnapshot signal = signals.getFirst();
        assertEquals(0.5, signal.rms());
        assertEquals(0.5, signal.peak());
        assertEquals(-0.5f, signal.sampleAt(0));
        assertSame(VoiceSignalSnapshot.silence(), signals.getLast());
        assertFalse(pipeline.isSpeaking());
        assertFalse(pipeline.canListen());
    }

    @Test
    void multiSampleChunkShouldPublishOneCompleteSignalThenSilence() {
        List<VoiceSignalSnapshot> signals = new ArrayList<>();
        float[] samples = {0.25f, -0.5f, 0.75f, 0.0f};
        AudioPipeline pipeline = new AudioPipeline(tts(text -> new TtsAudio(samples, 16_000)),
                new TrackingPlayback(), null, new AssistantAudioController(),
                AssistantActivityListener.noop(), signals::add);

        pipeline.speak("hola");

        assertEquals(2, signals.size(), "One signal per chunk followed by final silence");
        VoiceSignalSnapshot signal = signals.getFirst();
        assertEquals(4, signal.sampleCount());
        for (int i = 0; i < samples.length; i++) {
            assertEquals(samples[i], signal.sampleAt(i));
        }
        assertEquals(Math.sqrt(0.21875), signal.rms(), 1e-12);
        assertEquals(0.75, signal.peak());
        assertEquals(0.0, signal.vadProbability());
        assertSame(VoiceSignalSnapshot.silence(), signals.getLast());
    }

    @Test
    void playbackShouldPublishOneSignalPerNonEmptyChunkWithIndependentMetrics() {
        List<VoiceSignalSnapshot> signals = new ArrayList<>();
        AudioPlaybackService playback = new AudioPlaybackService() {
            @Override
            public void play(AudioDeviceInfo device, TtsAudio audio, float gain, PlaybackSignalListener listener) {
                listener.onSamples(new float[]{0.25f, -0.5f, 0.75f, 0.0f});
                listener.onSamples(new float[0]);
                listener.onSamples(new float[]{-0.125f, 0.125f});
            }
        };
        AudioPipeline pipeline = new AudioPipeline(tts(text -> new TtsAudio(new float[]{0}, 16_000)),
                playback, null, new AssistantAudioController(), AssistantActivityListener.noop(), signals::add);

        pipeline.speak("hola");

        assertEquals(3, signals.size());
        assertEquals(4, signals.get(0).sampleCount());
        assertEquals(Math.sqrt(0.21875), signals.get(0).rms(), 1e-12);
        assertEquals(0.75, signals.get(0).peak());
        assertEquals(2, signals.get(1).sampleCount());
        assertEquals(0.125, signals.get(1).rms());
        assertEquals(0.125, signals.get(1).peak());
        assertSame(VoiceSignalSnapshot.silence(), signals.get(2));
    }

    @Test
    void emptyPlaybackChunkShouldOnlyPublishFinalSilence() {
        List<VoiceSignalSnapshot> signals = new ArrayList<>();
        AudioPipeline pipeline = new AudioPipeline(tts(text -> new TtsAudio(new float[0], 16_000)),
                new TrackingPlayback(), null, new AssistantAudioController(),
                AssistantActivityListener.noop(), signals::add);

        pipeline.speak("hola");

        assertEquals(List.of(VoiceSignalSnapshot.silence()), signals);
    }

    @Test
    void synthesisFailureShouldPublishIdleAndSilenceWithoutSpeaking() {
        List<AssistantActivityState> activity = new ArrayList<>();
        List<VoiceSignalSnapshot> signals = new ArrayList<>();
        AudioPipeline pipeline = new AudioPipeline(tts(text -> { throw new IllegalStateException("tts"); }),
                new TrackingPlayback(), null, new AssistantAudioController(), activity::add, signals::add);
        assertTrue(pipeline.beginProcessing());
        assertThrows(IllegalStateException.class, () -> pipeline.speak("hola"));
        assertEquals(List.of(AssistantActivityState.PROCESSING, AssistantActivityState.IDLE), activity);
        assertEquals(List.of(VoiceSignalSnapshot.silence()), signals);
        assertTrue(pipeline.canListen());
    }

    @Test
    void shouldAllowOnlyOneProcessingOperationUntilFinished() {
        AudioPipeline pipeline = pipeline(tts(text -> new TtsAudio(new float[]{0}, 16_000)), new TrackingPlayback());

        assertTrue(pipeline.beginProcessing());
        assertEquals(AudioState.PROCESSING, pipeline.state());
        assertTrue(pipeline.isProcessing());
        assertFalse(pipeline.beginProcessing());

        pipeline.finishProcessing();

        assertEquals(AudioState.LISTENING, pipeline.state());
        assertTrue(pipeline.canListen());
    }

    @Test
    void shouldSynthesizeAndPlaySpeechThenApplyListeningGuard() {
        AtomicReference<String> synthesized = new AtomicReference<>();
        TtsAudio audio = new TtsAudio(new float[]{0.1f}, 16_000);
        TrackingPlayback playback = new TrackingPlayback();
        AudioPipeline pipeline = pipeline(tts(text -> { synthesized.set(text); return audio; }), playback);

        pipeline.speak("hola");

        assertEquals("hola", synthesized.get());
        assertSame(audio, playback.audio);
        assertEquals(1.0f, playback.gain);
        assertEquals(AudioState.LISTENING, pipeline.state());
        assertFalse(pipeline.canListen());
    }

    @Test
    void synthesisFailureShouldRestoreListeningWithoutPlaybackGuard() {
        AudioPipeline pipeline = pipeline(tts(text -> { throw new IllegalStateException("tts"); }), new TrackingPlayback());

        assertThrows(IllegalStateException.class, () -> pipeline.speak("hola"));

        assertEquals(AudioState.LISTENING, pipeline.state());
        assertTrue(pipeline.canListen());
    }

    @Test
    void playbackFailureShouldRestoreListeningAndApplyGuard() {
        TrackingPlayback playback = new TrackingPlayback();
        playback.failure = new IllegalStateException("audio");
        AudioPipeline pipeline = pipeline(tts(text -> new TtsAudio(new float[]{0}, 16_000)), playback);

        assertThrows(IllegalStateException.class, () -> pipeline.speak("hola"));

        assertEquals(AudioState.LISTENING, pipeline.state());
        assertFalse(pipeline.canListen());
    }

    @Test
    void shouldUseMutedGainFromSharedController() {
        TrackingPlayback playback = new TrackingPlayback();
        AssistantAudioController controller = new AssistantAudioController();
        controller.mute();
        AudioDeviceInfo device = new AudioDeviceInfo(null, "test", "test", "test");
        AudioPipeline pipeline = new AudioPipeline(
                tts(text -> new TtsAudio(new float[]{0.1f}, 16_000)), playback, device, controller);

        pipeline.speak("confirmación silenciosa");

        assertEquals(0.0f, playback.gain);
        assertNotNull(playback.audio, "Muted gain must be verified on an intercepted playback call");
    }

    private AudioPipeline pipeline(TtsEngine engine, TrackingPlayback playback) {
        AudioDeviceInfo device = new AudioDeviceInfo(null, "test", "test", "test");
        return new AudioPipeline(engine, playback, device, new AssistantAudioController());
    }

    private TtsEngine tts(Function<String, TtsAudio> synthesis) {
        return new TtsEngine() {
            @Override public TtsAudio synthesize(String text) { return synthesis.apply(text); }
            @Override public void close() { }
        };
    }

    private static final class TrackingPlayback extends AudioPlaybackService {
        private TtsAudio audio;
        private float gain;
        private RuntimeException failure;

        @Override
        public void play(AudioDeviceInfo device, TtsAudio audio, float gain, PlaybackSignalListener listener) {
            this.audio = audio;
            this.gain = gain;
            if (failure != null) throw failure;
            listener.onSamples(audio.samples());
        }
    }
}

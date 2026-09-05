package com.fuad.pipeline;

import com.fuad.audio.AudioDeviceInfo;
import com.fuad.audio.AudioPlaybackService;
import com.fuad.audio.AssistantAudioController;
import com.fuad.enums.AudioState;
import com.fuad.tts.TtsAudio;
import com.fuad.tts.TtsEngine;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class AudioPipelineTest {
    @Test
    void shouldAllowOnlyOneProcessingOperationUntilFinished() {
        AudioPipeline pipeline = pipeline(tts(text -> new TtsAudio(new float[]{0}, 16_000)), new TrackingPlayback());

        assertTrue(pipeline.beginProcessing());
        assertEquals(AudioState.PROCESSING, pipeline.getState());
        assertTrue(pipeline.isProcessing());
        assertFalse(pipeline.beginProcessing());

        pipeline.finishProcessing();

        assertEquals(AudioState.LISTENING, pipeline.getState());
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
        assertEquals(AudioState.LISTENING, pipeline.getState());
        assertFalse(pipeline.canListen());
    }

    @Test
    void synthesisFailureShouldRestoreListeningWithoutPlaybackGuard() {
        AudioPipeline pipeline = pipeline(tts(text -> { throw new IllegalStateException("tts"); }), new TrackingPlayback());

        assertThrows(IllegalStateException.class, () -> pipeline.speak("hola"));

        assertEquals(AudioState.LISTENING, pipeline.getState());
        assertTrue(pipeline.canListen());
    }

    @Test
    void playbackFailureShouldRestoreListeningAndApplyGuard() {
        TrackingPlayback playback = new TrackingPlayback();
        playback.failure = new IllegalStateException("audio");
        AudioPipeline pipeline = pipeline(tts(text -> new TtsAudio(new float[]{0}, 16_000)), playback);

        assertThrows(IllegalStateException.class, () -> pipeline.speak("hola"));

        assertEquals(AudioState.LISTENING, pipeline.getState());
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
        public void play(AudioDeviceInfo device, TtsAudio audio, float gain) {
            this.audio = audio;
            this.gain = gain;
            if (failure != null) throw failure;
        }
    }
}

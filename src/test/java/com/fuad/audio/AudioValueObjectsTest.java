package com.fuad.audio;

import com.fuad.speech.SpeechSegment;
import com.fuad.tts.TtsAudio;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class AudioValueObjectsTest {
    @Test
    void audioRecordsShouldPreserveOwnedBuffersWithoutCopies() {
        float[] samples = {0.25f, -0.5f};
        assertSame(samples, new AudioFrame(samples, 16_000, 0).samples());
        assertSame(samples, new SpeechSegment(samples, 16_000, 0).samples());
        assertSame(samples, new TtsAudio(samples, 16_000).samples());
        assertSame(samples, new com.fuad.tts.piper.PiperResponse(0, 16_000, samples, "").samples());
    }

    @Test
    void audioFrameShouldCalculateSampleCountAndDuration() {
        AudioFrame frame = new AudioFrame(new float[800], 16_000, 123L);

        assertEquals(800, frame.sampleCount());
        assertEquals(50.0, frame.durationMillis());
        assertEquals(123L, frame.timestampNanos());
    }

    @Test
    void speechSegmentShouldCalculateSampleCountAndDuration() {
        SpeechSegment segment = new SpeechSegment(new float[1_600], 16_000, 456L);

        assertEquals(1_600, segment.sampleCount());
        assertEquals(100.0, segment.durationMillis());
        assertEquals(456L, segment.startTimestampNanos());
    }

    @Test
    void ttsAudioShouldCalculateSampleCountAndDuration() {
        TtsAudio audio = new TtsAudio(new float[24_000], 24_000);

        assertEquals(24_000, audio.sampleCount());
        assertEquals(1.0, audio.durationSeconds());
    }
}

package com.fuad.audio;

import com.fuad.speech.SpeechSegment;
import com.fuad.tts.TtsAudio;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AudioValueObjectsTest {
    @Test
    void audioFrameShouldCalculateSampleCountAndDuration() {
        AudioFrame frame = new AudioFrame(new float[800], 16_000, 123L);

        assertEquals(800, frame.getSamplesCount());
        assertEquals(50.0, frame.durationMillis());
        assertEquals(123L, frame.getTimestampNanos());
    }

    @Test
    void speechSegmentShouldCalculateSampleCountAndDuration() {
        SpeechSegment segment = new SpeechSegment(new float[1_600], 16_000, 456L);

        assertEquals(1_600, segment.getSamplesCount());
        assertEquals(100.0, segment.durationMillis());
        assertEquals(456L, segment.getStartTimestampNanos());
    }

    @Test
    void ttsAudioShouldCalculateSampleCountAndDuration() {
        TtsAudio audio = new TtsAudio(new float[24_000], 24_000);

        assertEquals(24_000, audio.getSamplesCount());
        assertEquals(1.0, audio.durationSeconds());
    }
}

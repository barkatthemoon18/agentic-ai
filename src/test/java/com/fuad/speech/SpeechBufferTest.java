package com.fuad.speech;

import com.fuad.audio.AudioFrame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SpeechBufferTest {
    @Test
    void emptyBufferShouldExposeEmptyStateAndRejectSegmentCreation() {
        SpeechBuffer buffer = new SpeechBuffer();

        assertTrue(buffer.isEmpty());
        assertEquals(0, buffer.frameCount());
        assertEquals(0, buffer.durationMillis());
        assertThrows(IllegalStateException.class, buffer::toSegment);
    }

    @Test
    void shouldConcatenateFramesPreservingOrderAndFirstFrameMetadata() {
        SpeechBuffer buffer = new SpeechBuffer();
        buffer.add(new AudioFrame(new float[]{0.1f, 0.2f}, 1_000, 100L));
        buffer.add(new AudioFrame(new float[]{0.3f, 0.4f, 0.5f}, 1_000, 200L));

        SpeechSegment segment = buffer.toSegment();

        assertArrayEquals(new float[]{0.1f, 0.2f, 0.3f, 0.4f, 0.5f}, segment.getSamples());
        assertEquals(1_000, segment.getSampleRate());
        assertEquals(100L, segment.getStartTimestampNanos());
        assertEquals(2, buffer.frameCount());
        assertEquals(5, buffer.durationMillis());
    }

    @Test
    void clearShouldRemoveAllFrames() {
        SpeechBuffer buffer = new SpeechBuffer();
        buffer.add(new AudioFrame(new float[]{1}, 1_000, 0));

        buffer.clear();

        assertTrue(buffer.isEmpty());
    }
}

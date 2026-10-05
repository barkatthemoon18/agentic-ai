package com.fuad.audio;

import com.fuad.tts.TtsAudio;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import javax.sound.sampled.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AudioPlaybackServiceTest {
    private final Mixer mixer = mock(Mixer.class);
    private final SourceDataLine line = mock(SourceDataLine.class);
    private final AudioDeviceInfo device = new AudioDeviceInfo(null, "test", "test", "test");

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 512, 513, 1025})
    void playbackShouldChunkSignalsAndWritePcmThenDrainAndClose(int sampleCount) throws Exception {
        when(mixer.getLine(any(DataLine.Info.class))).thenReturn(line);
        List<float[]> signals = new ArrayList<>();
        List<byte[]> writes = new ArrayList<>();
        when(line.write(any(byte[].class), anyInt(), anyInt())).thenAnswer(invocation -> {
            byte[] pcm = invocation.getArgument(0);
            writes.add(pcm.clone());
            assertEquals(0, (int) invocation.getArgument(1));
            assertEquals(pcm.length, (int) invocation.getArgument(2));
            return pcm.length;
        });
        float[] samples = new float[sampleCount];
        java.util.Arrays.fill(samples, 0.5f);
        try (var audioSystem = mockStatic(AudioSystem.class)) {
            audioSystem.when(() -> AudioSystem.getMixer(null)).thenReturn(mixer);
            new AudioPlaybackService().play(device, new TtsAudio(samples, 16_000), 0.5f, signals::add);
        }

        int chunks = (sampleCount + 511) / 512;
        assertEquals(chunks, signals.size());
        assertEquals(chunks, writes.size());
        assertEquals(sampleCount, signals.stream().mapToInt(chunk -> chunk.length).sum());
        assertEquals(sampleCount * 2, writes.stream().mapToInt(pcm -> pcm.length).sum());
        for (int i = 0; i < signals.size(); i++) {
            assertTrue(signals.get(i).length <= 512);
            assertEquals(0.5f, signals.get(i)[0], "Visual samples precede gain conversion");
            assertEquals((byte) 0xff, writes.get(i)[0]);
            assertEquals((byte) 0x1f, writes.get(i)[1]);
            assertNotSame(samples, signals.get(i));
        }
        ArgumentCaptor<AudioFormat> format = ArgumentCaptor.forClass(AudioFormat.class);
        var order = inOrder(line);
        order.verify(line).open(format.capture());
        order.verify(line).start();
        if (chunks > 0) order.verify(line, times(chunks)).write(any(byte[].class), eq(0), anyInt());
        order.verify(line).drain();
        order.verify(line).stop();
        order.verify(line).close();
        assertEquals(16_000, format.getValue().getSampleRate());
        assertEquals(16, format.getValue().getSampleSizeInBits());
        assertEquals(1, format.getValue().getChannels());
        assertFalse(format.getValue().isBigEndian());
    }

    @Test
    void pcmShouldClampSamplesAndEncodeSignedLittleEndianWithoutMutatingInput() throws Exception {
        when(mixer.getLine(any(DataLine.Info.class))).thenReturn(line);
        float[] samples = {2, -2, 0, 0.5f};
        try (var audioSystem = mockStatic(AudioSystem.class)) {
            audioSystem.when(() -> AudioSystem.getMixer(null)).thenReturn(mixer);
            new AudioPlaybackService().play(device, new TtsAudio(samples, 16_000), 1);
        }
        ArgumentCaptor<byte[]> pcm = ArgumentCaptor.forClass(byte[].class);
        verify(line).write(pcm.capture(), eq(0), eq(8));
        assertArrayEquals(new byte[]{(byte) 0xff, 0x7f, 1, (byte) 0x80, 0, 0, (byte) 0xff, 0x3f}, pcm.getValue());
        assertArrayEquals(new float[]{2, -2, 0, 0.5f}, samples);
    }

    @Test
    void unavailableLineShouldPropagateFailureWithoutStartingPlayback() throws Exception {
        LineUnavailableException failure = new LineUnavailableException("no device");
        when(mixer.getLine(any(DataLine.Info.class))).thenThrow(failure);
        try (var audioSystem = mockStatic(AudioSystem.class)) {
            audioSystem.when(() -> AudioSystem.getMixer(null)).thenReturn(mixer);
            RuntimeException thrown = assertThrows(RuntimeException.class,
                    () -> new AudioPlaybackService().play(device, new TtsAudio(new float[]{0}, 16_000), 1));
            assertSame(failure, thrown.getCause());
        }
        verifyNoInteractions(line);
    }
}

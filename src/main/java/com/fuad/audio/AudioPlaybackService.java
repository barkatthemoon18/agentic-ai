package com.fuad.audio;

import com.fuad.tts.TtsAudio;

import javax.sound.sampled.*;

public class AudioPlaybackService {
    private static final int VISUAL_CHUNK_SAMPLES = 512;

    public void play(AudioDeviceInfo device, TtsAudio audio, float gain) {
        play(device, audio, gain, PlaybackSignalListener.noop());
    }

    public void play(AudioDeviceInfo device, TtsAudio audio, float gain, PlaybackSignalListener listener) {
        AudioFormat format = new AudioFormat(audio.sampleRate(), 16, 1, true, false);
        Mixer mixer = AudioSystem.getMixer(device.info());
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
        try {
            System.out.println("PLAYBACK -> getLine");
            SourceDataLine line = (SourceDataLine) mixer.getLine(info);
            System.out.println("PLAYBACK -> open");
            line.open(format);
            System.out.println("PLAYBACK -> start");
            line.start();
            System.out.println("PLAYBACK -> write");
            playChunks(line, audio.samples(), gain, listener);
            System.out.println("PLAYBACK -> drain");
            line.drain();
            System.out.println("PLAYBACK -> stop");
            line.stop();
            System.out.println("PLAYBACK -> close");
            line.close();
            System.out.println("PLAYBACK -> done");
        }
        catch (LineUnavailableException e) {
            throw new RuntimeException("Unable to play TTS audio", e);
        }
    }

    private void playChunks(SourceDataLine line, float[] samples, float gain, PlaybackSignalListener listener) {
        for (int i = 0; i < samples.length; i += VISUAL_CHUNK_SAMPLES) {
            int length = Math.min(VISUAL_CHUNK_SAMPLES, samples.length - i);
            float[] chunk = new float[length];
            System.arraycopy(samples, i, chunk, 0, length);
            listener.onSamples(chunk);
            byte[] pcm = floatToPcm16(chunk, gain);
            line.write(pcm, 0, pcm.length);
        }
    }

    private byte[] floatToPcm16(float[] samples, float gain) {
        byte[] pcm = new byte[samples.length * 2];
        for (int i = 0; i < samples.length; i++) {
            float sample = samples[i] * gain;
            sample = Math.clamp(sample, -1.0f, 1.0f);
            short value = (short) (sample * 32767.0f);
            pcm[i * 2] = (byte) (value & 0xFF);
            pcm[i * 2 + 1] = (byte) ((value >> 8) & 0xFF);
        }
        return pcm;
    }
}

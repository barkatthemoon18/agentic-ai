package com.fuad.audio;

import com.fuad.config.AppConfig;
import com.fuad.tts.TtsAudio;

import javax.sound.sampled.*;

public class AudioPlaybackService {
    public void play(AudioDeviceInfo device, TtsAudio audio) {
        AudioFormat format = new AudioFormat(audio.getSampleRate(), 16, 1, true, false);
        Mixer mixer = AudioSystem.getMixer(device.getInfo());
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
        try {
            SourceDataLine line = (SourceDataLine) mixer.getLine(info);
            line.open(format);
            line.start();
            byte[] pcm = floatToPcm16(audio.getSamples());
            line.write(pcm, 0, pcm.length);
            line.drain();
            line.stop();
            line.close();
        }
        catch (LineUnavailableException e) {
            throw new RuntimeException("Unable to play TTS audio", e);
        }
    }

    private byte[] floatToPcm16(float[] samples) {
        byte[] pcm = new byte[samples.length * 2];
        for (int i = 0; i < samples.length; i++) {
            float sample = Math.clamp(samples[i], -1.0f, 1.0f);
            short value = (short) (sample * 32767.0f);
            pcm[i * 2] = (byte) (value & 0xFF);
            pcm[i * 2 + 1] = (byte) ((value >> 8) & 0xFF);
        }
        return pcm;
    }
}

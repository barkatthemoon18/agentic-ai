package com.fuad.audio;

import javax.sound.sampled.*;

public class AudioCaptureService {
    private TargetDataLine line;
    private Thread captureThread;
    private volatile boolean running;

    public void start(AudioDeviceInfo device, AudioFrameListener listener) throws LineUnavailableException {
        Mixer mixer = AudioSystem.getMixer(device.getInfo());
        AudioFormat format = new AudioFormat(16000, 16, 2, true, false);
        DataLine.Info lineInfo = new DataLine.Info(TargetDataLine.class, format);
        line = (TargetDataLine) mixer.getLine(lineInfo);
        line.open(format);
        line.start();
        System.out.println("Captura iniciada");
        running = true;
        captureThread = new Thread(() -> captureLoop(listener), "audio-capture");
        captureThread.start();
        System.out.println("Thread iniciado");
    }

    public void stop()  {
        running = false;
        if (line != null) {
            line.stop();
            line.close();
        }
        if (captureThread != null) {
            captureThread.interrupt();
        }
    }

    public boolean isRunning() {
        return running;
    }

    private void captureLoop(AudioFrameListener listener) {
        byte[] buffer = new byte[2048];

        while (running) {
            int bytesRead = line.read(buffer, 0, buffer.length);
            if (bytesRead < 0) {
                continue;
            }
            float[] samples = pcm16ToFloat(buffer, bytesRead);
            AudioFrame frame = new AudioFrame(getChannel(samples, 1, 2), 16000, System.nanoTime());
            listener.onFrame(frame);
        }
    }

    private float[] getChannel(float[] interleavedSamples, int channelIndex, int channelCount) {
        float[] mono = new float[interleavedSamples.length / 2];

        for (int i = 0; i < mono.length; i++) {
            mono[i] = interleavedSamples[i * channelCount + channelIndex];
        }
        return mono;
    }

    private float[] pcm16ToFloat(byte[] data, int bytesRead) {
        int samplesCount = bytesRead / 2;
        float[] samples = new float[samplesCount];

        for (int i = 0; i < samplesCount; i++) {
            int low = data[i * 2] & 0xFF;
            int high = data[i * 2 + 1];
            short value = (short) ((high << 8) | low);
            samples[i] = value / 32768.0f;
        }
        return samples;
    }
}

package com.fuad.tts;

public class MockTtsEngine implements TtsEngine{
    private static final int SAMPLE_RATE = 24000;
    private static final double FREQUENCY = 440.0;
    private static final double DURATION_SECONDS = 1.0;
    private static final float AMPLITUDE = 0.25f;

    @Override
    public TtsAudio synthesize(String text) {
        System.out.println("[MOCK TTS] Synthesizing: " + text);
        int sampleCount = (int) (SAMPLE_RATE * DURATION_SECONDS);
        float[] samples = new float[sampleCount];
        for (int i = 0; i < sampleCount; i++) {
            samples[i] = AMPLITUDE * (float) Math.sin(2.0 * Math.PI * FREQUENCY * i / SAMPLE_RATE);
        }
        return new TtsAudio(samples, SAMPLE_RATE);
    }

    @Override
    public void close() {
        /* Ignored */
    }
}

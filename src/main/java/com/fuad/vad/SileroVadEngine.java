package com.fuad.vad;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.fuad.audio.AudioFrame;

import java.util.HashMap;
import java.util.Map;

public class SileroVadEngine implements VadEngine {
    private static final int SAMPLE_RATE = 16000;
    private static final int FRAME_SIZE = 512;
    private static final int CONTEXT_SIZE = 64;
    private static final int STATE_SIZE = 128;
    private final OrtEnvironment environment;
    private final OrtSession session;
    private final float threshold;
    private float[][][] state;
    private float[] context;

    public SileroVadEngine(String modelPath, float threshold) {
        this.threshold = threshold;
        try {
            environment = OrtEnvironment.getEnvironment();
            OrtSession.SessionOptions options = new OrtSession.SessionOptions();
            session = environment.createSession(modelPath, options);
            reset();
            System.out.println("Silero inputs: " + session.getInputNames());
            System.out.println("Silero outputs: " + session.getOutputNames());
            session.getInputInfo().forEach((name, info) -> System.out.println("INPUT " + name + " -> " + info));
            session.getOutputInfo().forEach((name, info) -> System.out.println("OUTPUT " + name + " -> " + info));
        }
        catch (OrtException e) {
            throw new RuntimeException("Unable to initialize SileroVadEngine.", e);
        }
    }

    @Override
    public VadResult process(AudioFrame frame) {
        validate(frame);
        float[][] input = new float[1][CONTEXT_SIZE + FRAME_SIZE];
        System.arraycopy(context, 0, input[0], 0, CONTEXT_SIZE);
        System.arraycopy(frame.getSamples(), 0, input[0], CONTEXT_SIZE, FRAME_SIZE);
        try (OnnxTensor inputTensor = OnnxTensor.createTensor(environment, input);
             OnnxTensor stateTensor = OnnxTensor.createTensor(environment, state);
             OnnxTensor srTensor = OnnxTensor.createTensor(environment, (long) SAMPLE_RATE)) {
            /* Inferencia */
            Map<String, OnnxTensor> inputs = new HashMap<>();
            inputs.put("input", inputTensor);
            inputs.put("state", stateTensor);
            inputs.put("sr", srTensor);
            OrtSession.Result result = session.run(inputs);
            OnnxTensor outputTensor = (OnnxTensor) result.get("output").orElseThrow();
            float[][] output = (float[][]) outputTensor.getValue();
            float probability = output[0][0];
            OnnxTensor newStateTensor = (OnnxTensor) result.get("stateN").orElseThrow();
            state = (float[][][]) newStateTensor.getValue();
            System.arraycopy(frame.getSamples(), FRAME_SIZE - CONTEXT_SIZE, context, 0, CONTEXT_SIZE);
            boolean speech = probability >= threshold;
            return new VadResult(probability, speech);
        }
        catch (OrtException e) {
            throw new RuntimeException("Unable to process SileroVadEngine.", e);
        }
    }

    @Override
    public void reset() {
        state = new float[2][1][STATE_SIZE];
        context = new float[CONTEXT_SIZE];
    }

    @Override
    public void close() {
        try {
            session.close();
        }
        catch (OrtException e) {
            throw new RuntimeException(e);
        }
    }

    private void validate(AudioFrame frame) {
        if (frame.getSampleRate() != SAMPLE_RATE) {
            throw new IllegalArgumentException("Silero requires 16Khz");
        }
        if (frame.getSamples().length != FRAME_SIZE) {
            throw new IllegalArgumentException("Silero requires exactly " + FRAME_SIZE + " samples");
        }
    }
}

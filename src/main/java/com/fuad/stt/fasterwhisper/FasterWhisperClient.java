package com.fuad.stt.fasterwhisper;

import com.fuad.config.AppConfig;
import com.fuad.speech.SpeechSegment;
import com.fuad.stt.TranscriptionResult;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

public class FasterWhisperClient implements AutoCloseable {
    private Process process;
    private DataOutputStream outputStream;
    private DataInputStream inputStream;
    private final AtomicLong requestCounter = new AtomicLong();

    public void start() {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("python", "python/whisper_worker.py");
            process = processBuilder.start();
            outputStream = new DataOutputStream(new BufferedOutputStream(process.getOutputStream()));
            inputStream = new DataInputStream(new BufferedInputStream(process.getInputStream()));
            Thread.ofPlatform().name("whisper-worker-log")
                    .daemon(true)
                    .start(() -> {
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                            reader.lines().forEach(line -> System.out.println("[Whisper] " + line));
                        }
                        catch (IOException e) {
                            /* Ignored */
                        }
                    });
            waitUntilReady();
        }
        catch (IOException e) {
            throw new RuntimeException("Unable to start FasterWhisper worker", e);
        }
    }

    public synchronized TranscriptionResult transcribe(SpeechSegment segment) {
        if (!isAlive()) {
            throw new IllegalStateException("FasterWhisper worker is not running");
        }
        try {
            long requestId = sendTranscriptionRequest(segment);
            return readTranscriptionResponse(requestId);
        }
        catch (IOException e) {
            throw new RuntimeException("Unable to transcribe FasterWhisper worker", e);
        }
    }

    public boolean ping() {
        if (!isAlive()) {
            return false;
        }
        try {
            long requestId = requestCounter.incrementAndGet();
            outputStream.writeInt(AppConfig.MAGIC_REQUEST);
            outputStream.writeByte(AppConfig.VERSION);
            outputStream.writeByte(AppConfig.OP_PING);
            outputStream.writeShort(0);
            outputStream.writeLong(requestId);
            outputStream.flush();
            TranscriptionResult response = readTranscriptionResponse(requestId);
            return "Working...".equals(response.getText());
        }
        catch (IOException e) {
            throw new RuntimeException("FasterWhisper ping failed", e);
        }
    }

    public boolean isAlive() {
        return process != null && process.isAlive();
    }

    private void waitUntilReady() throws IOException {
        long deadline = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < deadline) {
            if (!isAlive()) {
                throw new IllegalStateException("FasterWhisper worker treminated during startup");
            }
            try {
                if (ping()) {
                    return;
                }
            }
            catch (Exception e) {
                /* Ignored purposedly */
            }
            try {
                Thread.sleep(200);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
        throw new IllegalStateException("FasterWhisper worker startup timeout");
    }

    private long sendTranscriptionRequest(SpeechSegment segment) throws IOException{
        long requestId = requestCounter.incrementAndGet();
        outputStream.writeInt(AppConfig.MAGIC_REQUEST);
        outputStream.writeByte(AppConfig.VERSION);
        outputStream.writeByte(AppConfig.OP_TRANSCRIBE);
        outputStream.writeShort(0);
        outputStream.writeLong(requestId);
        outputStream.writeLong(segment.getStartTimestampNanos());
        outputStream.writeInt(segment.getSampleRate());
        outputStream.writeInt(segment.getSamplesCount());
        for (float sample : segment.getSamples()) {
            outputStream.writeFloat(sample);
        }
        outputStream.flush();
        return requestId;
    }

    private TranscriptionResult readTranscriptionResponse(long expectedRequestId) throws IOException {
        int magic = inputStream.readInt();
        if (magic != AppConfig.MAGIC_RESPONSE) {
            throw new IOException("Invalid FasterWhisper response magic");
        }
        byte version = inputStream.readByte();
        if (version != AppConfig.VERSION) {
            throw new IOException("Unsupported protocol version: " + version);
        }
        byte status = inputStream.readByte();
        inputStream.readShort();
        long requestId = inputStream.readLong();
        if (requestId != expectedRequestId) {
            throw new IOException("Unexpected request ID: " + requestId);
        }
        double durationSeconds = inputStream.readDouble();
        int languageLength = inputStream.readInt();
        int textLength = inputStream.readInt();
        byte[] languageBytes = inputStream.readNBytes(languageLength);
        byte[] textBytes = inputStream.readNBytes(textLength);
        String language = new String(languageBytes, StandardCharsets.UTF_8);
        String text = new String(textBytes, StandardCharsets.UTF_8);
        if (status != 0) {
            throw new RuntimeException("FasterWhisper error: " + text);
        }
        return new TranscriptionResult(text, language, durationSeconds);
    }

    @Override
    public void close() {
        process.destroy();
    }
}

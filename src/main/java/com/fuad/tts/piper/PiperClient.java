package com.fuad.tts.piper;

import com.fuad.tts.TtsAudio;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

public class PiperClient implements AutoCloseable {
    private static final int MAGIC_REQUEST = 0x50545453;   // PTTS
    private static final int MAGIC_RESPONSE = 0x50545452;  // PTTR
    private static final int VERSION = 1;
    private static final int OP_SYNTHESIZE = 1;
    private static final int OP_PING = 2;
    private static final int OP_SHUTDOWN = 3;
    private static final int STATUS_OK = 0;
    private final AtomicLong requestCounter = new AtomicLong();
    private Process process;
    private DataInputStream inputStream;
    private DataOutputStream outputStream;

    public void start() {
        try {
            if (process != null && process.isAlive()) {
                return;
            }
            ProcessBuilder processBuilder = new ProcessBuilder("python", "python/piper_worker.py");
            process = processBuilder.start();
            outputStream = new DataOutputStream(new BufferedOutputStream(process.getOutputStream()));
            inputStream = new DataInputStream(new BufferedInputStream(process.getInputStream()));
            Thread.ofPlatform().name("piper-worker-log")
                    .daemon(true)
                    .start(() -> {
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                            reader.lines().forEach(line -> System.out.println("[Piper] " + line));
                        }
                        catch (IOException e) {
                            /* Ignored */
                        }
                    });
            waitUntilReady();
        }
        catch (IOException e) {
            throw new RuntimeException("Unable to start Piper worker", e);
        }
    }

    public synchronized TtsAudio synthesize(String text) {
        if (process == null || !process.isAlive()) {
            throw new IllegalStateException("Piper worker not started");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("TTS text cannot be null or blank");
        }
        try {
            long requestId = requestCounter.incrementAndGet();
            byte[] textBytes = text.getBytes(StandardCharsets.UTF_8);
            writeAudioRequest(requestId, OP_SYNTHESIZE, textBytes);
            return readAudioResponse(requestId);
        }
        catch (IOException e) {
            throw new RuntimeException("Piper synthesis failed", e);
        }
    }

    public synchronized boolean ping() {
        if (process == null || !process.isAlive()) {
            throw new IllegalStateException("Piper worker not started");
        }
        try {
            long requestId = requestCounter.incrementAndGet();
            writeAudioRequest(requestId, OP_PING, new byte[0]);
            PiperResponse response = readResponse(requestId);
            return response.getStatus() == STATUS_OK && "Working...".equals(response.getMessage());
        }
        catch (IOException e) {
            return false;
        }
    }

    @Override
    public void close() throws Exception {
        if (process == null) {
            return;
        }
        try {
            if (process.isAlive()) {
                long requestId = requestCounter.incrementAndGet();
                writeAudioRequest(requestId, OP_SHUTDOWN, new byte[0]);
                readAudioResponse(requestId);
            }
        }
        catch (Exception e) {
            System.err.println("Unable to gracefully stop Piper: " + e.getMessage());
        }
        finally {
            try {
                if (outputStream != null) {
                    outputStream.close();
                }
                if (inputStream != null) {
                    inputStream.close();
                }
                if (process.isAlive()) {
                    process.destroy();
                }
                process = null;
            }
            catch (IOException e) {
                /* Ignored */
            }
        }
    }

    private void writeAudioRequest(long requestId, int op, byte[] payload) throws IOException {
        outputStream.writeInt(MAGIC_REQUEST);
        outputStream.writeByte(VERSION);
        outputStream.writeByte(op);
        outputStream.writeShort(0);
        outputStream.writeLong(requestId);
        outputStream.writeInt(payload.length);
        if (payload.length > 0) {
            outputStream.write(payload);
        }
        outputStream.flush();
    }

    private TtsAudio readAudioResponse(long requestId) throws IOException {
        PiperResponse response = readResponse(requestId);
        if (response.getStatus() != STATUS_OK) {
            throw new RuntimeException("Piper error: " + response.getMessage());
        }
        if (response.getSampleRate() <= 0) {
            throw new RuntimeException("Invalid Piper sample rate: " + response.getSampleRate());
        }
        if (response.getSampleCount() == 0) {
            throw new RuntimeException("Piper returned empty audio");
        }
        return new TtsAudio(response.getSamples(), response.getSampleRate());
    }

    private PiperResponse readResponse(long expectedRequestId) throws IOException {
        int magic = inputStream.readInt();
        if (magic != MAGIC_RESPONSE) {
            throw new IOException(String.format("Invalid Piper response magic: 0x%08X", magic));
        }
        int version = inputStream.readUnsignedByte();
        if (version != VERSION) {
            throw new IOException("Unsupported Piper protocol version: " + version);
        }
        int status = inputStream.readUnsignedByte();
        inputStream.readUnsignedShort();
        long requestId = inputStream.readLong();
        if (requestId != expectedRequestId) {
            throw new IOException("Unexpected Piper requestId. Expected " + expectedRequestId + ", received " + requestId);
        }
        int sampleRate = inputStream.readInt();
        int sampleCount = inputStream.readInt();
        int messageLength = inputStream.readInt();
        if (sampleCount < 0) {
            throw new IOException("Invalid Piper sample count: " + sampleCount);
        }
        if (messageLength < 0) {
            throw new IOException("Invalid Piper message length: " + messageLength);
        }
        String message = "";
        if (messageLength > 0) {
            byte[] messageBytes = inputStream.readNBytes(messageLength);
            if (messageBytes.length != messageLength) {
                throw new EOFException("Incomplete Piper message");
            }
            message = new String(messageBytes, StandardCharsets.UTF_8);
        }
        float[] samples = new float[sampleCount];
        for (int i = 0; i < sampleCount; i++) {
            samples[i] = inputStream.readFloat();
        }
        return new PiperResponse(status, sampleRate, samples, message);
    }

    private void waitUntilReady() throws IOException {
        System.out.println("Waiting for Piper worker to start...");
        if (!ping()) {
            throw new RuntimeException("Piper worker did not become ready");
        }
        System.out.println("Piper worker running properly: true");
    }
}

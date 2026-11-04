package com.fuad.tts.worker;

import com.fuad.tts.TtsAudio;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Client for a Python TTS worker process speaking the shared binary IPC protocol
 * (see {@code python/piper_worker.py} and {@code python/supertonic_worker.py}).
 */
public class TtsWorkerClient implements AutoCloseable {
    private static final int MAGIC_REQUEST = 0x50545453;   // PTTS
    private static final int MAGIC_RESPONSE = 0x50545452;  // PTTR
    private static final int VERSION = 1;
    private static final int OP_SYNTHESIZE = 1;
    private static final int OP_PING = 2;
    private static final int OP_SHUTDOWN = 3;
    private static final int STATUS_OK = 0;
    private final AtomicLong requestCounter = new AtomicLong();
    private final String name;
    private final List<String> command;
    private Process process;
    private DataInputStream inputStream;
    private DataOutputStream outputStream;

    protected TtsWorkerClient(String name, List<String> command) {
        this.name = Objects.requireNonNull(name);
        this.command = List.copyOf(command);
    }

    public void start() {
        try {
            if (process != null && process.isAlive()) {
                return;
            }
            final Process started = new ProcessBuilder(command).start();
            process = started;
            outputStream = new DataOutputStream(new BufferedOutputStream(started.getOutputStream()));
            inputStream = new DataInputStream(new BufferedInputStream(started.getInputStream()));
            Thread.ofPlatform().name(name.toLowerCase() + "-worker-log")
                    .daemon(true)
                    .start(() -> {
                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(started.getErrorStream(), StandardCharsets.UTF_8))) {
                            reader.lines().forEach(line -> System.out.println("[" + name + "] " + line));
                        }
                        catch (IOException e) {
                            /* Ignored */
                        }
                    });
            try {
                waitUntilReady();
            }
            catch (RuntimeException e) {
                killProcess();
                throw e;
            }
        }
        catch (IOException e) {
            throw new RuntimeException("Unable to start " + name + " worker", e);
        }
    }

    public synchronized TtsAudio synthesize(String text) {
        if (process == null || !process.isAlive()) {
            throw new IllegalStateException(name + " worker not started");
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
            // The stream may be out of sync; drop the process so a later start() begins clean.
            killProcess();
            throw new RuntimeException(name + " synthesis failed", e);
        }
    }

    public synchronized boolean ping() {
        if (process == null || !process.isAlive()) {
            throw new IllegalStateException(name + " worker not started");
        }
        try {
            long requestId = requestCounter.incrementAndGet();
            writeAudioRequest(requestId, OP_PING, new byte[0]);
            TtsWorkerResponse response = readResponse(requestId);
            return response.status() == STATUS_OK && "Working...".equals(response.message());
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
                TtsWorkerResponse response = readResponse(requestId);
                if (response.status() != STATUS_OK) {
                    throw new RuntimeException(name + " shutdown failed: " + response.message());
                }
            }
        }
        catch (Exception e) {
            System.err.println("Unable to gracefully stop " + name + ": " + e.getMessage());
        }
        finally {
            killProcess();
        }
    }

    public boolean isAlive() {
        return process != null && process.isAlive();
    }

    private void killProcess() {
        try {
            if (outputStream != null) {
                outputStream.close();
            }
            if (inputStream != null) {
                inputStream.close();
            }
        }
        catch (IOException e) {
            /* Ignored */
        }
        if (process != null && process.isAlive()) {
            process.destroy();
        }
        process = null;
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
        TtsWorkerResponse response = readResponse(requestId);
        if (response.status() != STATUS_OK) {
            throw new RuntimeException(name + " error: " + response.message());
        }
        if (response.sampleRate() <= 0) {
            throw new RuntimeException("Invalid " + name + " sample rate: " + response.sampleRate());
        }
        if (response.sampleCount() == 0) {
            throw new RuntimeException(name + " returned empty audio");
        }
        return new TtsAudio(response.samples(), response.sampleRate());
    }

    private TtsWorkerResponse readResponse(long expectedRequestId) throws IOException {
        int magic = inputStream.readInt();
        if (magic != MAGIC_RESPONSE) {
            throw new IOException(String.format("Invalid %s response magic: 0x%08X", name, magic));
        }
        int version = inputStream.readUnsignedByte();
        if (version != VERSION) {
            throw new IOException("Unsupported " + name + " protocol version: " + version);
        }
        int status = inputStream.readUnsignedByte();
        inputStream.readUnsignedShort();
        long requestId = inputStream.readLong();
        if (requestId != expectedRequestId) {
            throw new IOException("Unexpected " + name + " requestId. Expected " + expectedRequestId
                    + ", received " + requestId);
        }
        int sampleRate = inputStream.readInt();
        int sampleCount = inputStream.readInt();
        int messageLength = inputStream.readInt();
        if (sampleCount < 0) {
            throw new IOException("Invalid " + name + " sample count: " + sampleCount);
        }
        if (messageLength < 0) {
            throw new IOException("Invalid " + name + " message length: " + messageLength);
        }
        String message = "";
        if (messageLength > 0) {
            byte[] messageBytes = inputStream.readNBytes(messageLength);
            if (messageBytes.length != messageLength) {
                throw new EOFException("Incomplete " + name + " message");
            }
            message = new String(messageBytes, StandardCharsets.UTF_8);
        }
        float[] samples = new float[sampleCount];
        for (int i = 0; i < sampleCount; i++) {
            samples[i] = inputStream.readFloat();
        }
        return new TtsWorkerResponse(status, sampleRate, samples, message);
    }

    private void waitUntilReady() {
        System.out.println("Waiting for " + name + " worker to start...");
        if (!ping()) {
            throw new RuntimeException(name + " worker did not become ready");
        }
        System.out.println(name + " worker running properly: true");
    }
}

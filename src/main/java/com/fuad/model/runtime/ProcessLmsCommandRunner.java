package com.fuad.model.runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class ProcessLmsCommandRunner implements LmsCommandRunner {
    private static final Duration TERMINATION_GRACE = Duration.ofSeconds(2);
    private final Map<RuntimeComponent, Process> activeProcesses = new ConcurrentHashMap<>();

    @Override
    public CommandResult run(RuntimeComponent owner, List<String> command, Duration timeout)
            throws InterruptedException {
        return run(owner, command, timeout, new CommandGeneration());
    }

    @Override
    public CommandResult run(RuntimeComponent owner, List<String> command, Duration timeout,
                             CommandGeneration generation) throws InterruptedException {
        Process process;
        try {
            process = generation.start(command);
            if (process == null) {
                return new CommandResult(-1, "Recovery generation was cancelled", false);
            }
        }
        catch (IOException e) {
            return new CommandResult(-1, e.getMessage(), false);
        }
        Process previous = activeProcesses.putIfAbsent(owner, process);
        if (previous != null) {
            terminate(process);
            return new CommandResult(-1, "Another operation is already active for " + owner, false);
        }
        StringBuilder output = new StringBuilder();
        Thread reader = Thread.ofVirtual().start(() -> {
            try (var input = process.inputReader(StandardCharsets.UTF_8)) {
                String line;
                while ((line = input.readLine()) != null) {
                    if (output.length() < 32_000) {
                        output.append(line).append(System.lineSeparator());
                    }
                }
            }
            catch (IOException ignored) {
                // Process termination may close the stream while it is being read.
            }
        });
        boolean finished;
        try {
            finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                terminate(process);
            }
            reader.join(TERMINATION_GRACE);
            return new CommandResult(finished ? process.exitValue() : -1,
                    output.toString().trim(), !finished);
        }
        finally {
            activeProcesses.remove(owner, process);
            generation.clear(process);
        }
    }

    @Override
    public void cancel(RuntimeComponent owner) {
        Process process = activeProcesses.get(owner);
        if (process != null) {
            terminate(process);
        }
    }

    private static void terminate(Process process) {
        process.destroy();
        try {
            if (!process.waitFor(TERMINATION_GRACE.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
            }
        }
        catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
        }
    }
}

package com.fuad.model.runtime;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Coordinates process creation with invalidation of an obsolete recovery generation. */
public final class CommandGeneration {
    private boolean active = true;
    private Process process;

    synchronized Process start(List<String> command) throws IOException {
        if (!active) {
            return null;
        }
        process = new ProcessBuilder(List.copyOf(command))
                .redirectErrorStream(true)
                .start();
        return process;
    }

    public synchronized boolean isActive() {
        return active;
    }

    public synchronized void cancel() {
        active = false;
        if (process == null || !process.isAlive()) {
            return;
        }
        process.destroy();
        try {
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        }
        catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
        }
    }

    synchronized void clear(Process completed) {
        if (process == completed) {
            process = null;
        }
    }
}

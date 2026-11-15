package com.fuad.terminal;

import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public final class PowershellSession implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(PowershellSession.class);
    private static final List<String> SHELLS = List.of("pwsh.exe", "powershell.exe");
    private PtyProcess process;
    private boolean closed;
    private String error = "NOT STARTED";

    public synchronized void start() {
        if (closed || process != null) {
            return;
        }
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows")) {
            error = "PowerShell terminal requires Windows";
            return;
        }
        String directory = Path.of("").toAbsolutePath().normalize().toString();
        for (String exe : SHELLS) {
            try {
                process = new PtyProcessBuilder().setCommand(new String[] { exe, "-NoLogo", "-NoProfile" })
                        .setDirectory(directory).setEnvironment(System.getenv()).start();
                error = null;
                LOGGER.info("PowerShell terminal started: {}", exe);
                return;
            }
            catch (IOException | RuntimeException | LinkageError e) {
                error = "Unable to start " + exe;
                LOGGER.warn(error, e);
            }
        }
    }

    public synchronized Optional<PtyProcess> process() {
        if (closed) {
            return Optional.empty();
        }
        return Optional.ofNullable(process);
    }

    public synchronized boolean isRunning() {
        return !closed && process != null && process.isAlive();
    }

    public synchronized Optional<String> error() {
        return Optional.ofNullable(error);
    }

    @Override
    public synchronized void close() throws Exception {
        if (closed) {
            return;
        }
        closed = true;
        if (process == null) {
            return;
        }
        process.destroy();
        try {
            if (!process.waitFor(500, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
            }
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }
}
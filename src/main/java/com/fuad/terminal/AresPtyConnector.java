package com.fuad.terminal;

import com.pty4j.PtyProcess;
import com.pty4j.WinSize;
import com.techsenger.jeditermfx.core.ProcessTtyConnector;
import com.techsenger.jeditermfx.core.util.TermSize;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class AresPtyConnector extends ProcessTtyConnector {
    private final PtyProcess process;

    public AresPtyConnector(PtyProcess process) {
        super(Objects.requireNonNull(process), StandardCharsets.UTF_8);
        this.process = process;
    }

    @Override
    public void resize(@NotNull TermSize termSize) {
        if (!process.isAlive()) {
            return;
        }
        process.setWinSize(new WinSize(termSize.getColumns(), termSize.getRows()));
    }

    @Override
    public String getName() {
        return "ARES // POWERSHELL";
    }
}

package com.fuad.assistant.skills.os;

import java.io.IOException;
import java.nio.file.Path;

public class WindowsApplicationController implements ApplicationController {
    @Override
    public boolean open(ApplicationDefinition applicationDefinition) throws IOException {
        new ProcessBuilder(applicationDefinition.getOpenCommand()).start();
        return true;
    }

    @Override
    public boolean close(ApplicationDefinition applicationDefinition) {
        String expectedProcess = applicationDefinition.getProcessName();
        boolean found = false;
        for (ProcessHandle process : ProcessHandle.allProcesses().toList()) {
            String command = process.info().command().orElse("");
            if (command.isBlank()) {
                continue;
            }
            String processName;
            try {
                processName = Path.of(command).getFileName().toString();
            }
            catch (Exception e) {
                continue;
            }
            if (!processName.equalsIgnoreCase(expectedProcess)) {
                continue;
            }
            found = true;
            if (!process.destroy()) {
                process.destroyForcibly();
            }
        }
        return found;
    }
}

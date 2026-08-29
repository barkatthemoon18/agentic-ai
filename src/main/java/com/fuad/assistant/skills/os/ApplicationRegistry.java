package com.fuad.assistant.skills.os;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ApplicationRegistry {
    private final Map<String, List<String>> applications;

    public ApplicationRegistry(Map<String, List<String>> applications) {
        this.applications = applications;
    }

    public boolean open(String application) throws IOException {
        String normalized = application.toLowerCase(Locale.ROOT);
        List<String> command = applications.get(normalized);
        if (command == null) {
            return false;
        }
        new ProcessBuilder(command).start();
        return true;
    }
}

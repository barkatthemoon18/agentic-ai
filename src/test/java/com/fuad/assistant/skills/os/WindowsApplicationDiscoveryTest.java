package com.fuad.assistant.skills.os;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WindowsApplicationDiscoveryTest {
    @Test
    void shouldConvertStartAppsJsonToSafeDefinitions() throws Exception {
        String json = """
                [{"id":"Microsoft.VisualStudioCode","name":"Visual Studio Code",
                  "executablePath":"C:\\\\Apps\\\\Code.exe","packageRoot":null},
                 {"id":"SpotifyAB.SpotifyMusic_x!Spotify","name":"Spotify",
                  "executablePath":null,"packageRoot":"C:\\\\Program Files\\\\WindowsApps\\\\Spotify"}]
                """;
        WindowsApplicationDiscovery discovery = new WindowsApplicationDiscovery(
                (command, timeout) -> successful(json, command, timeout), new ObjectMapper());

        List<ApplicationDefinition> applications = discovery.discover();

        assertEquals(2, applications.size());
        assertEquals(List.of("explorer.exe", "shell:AppsFolder\\Microsoft.VisualStudioCode"),
                applications.getFirst().getOpenCommand());
        assertEquals("C:\\Apps\\Code.exe",
                applications.getFirst().getProcessIdentity().executablePaths().iterator().next());
        assertEquals("C:\\Program Files\\WindowsApps\\Spotify",
                applications.get(1).getProcessIdentity().packageRoots().iterator().next());
    }

    @Test
    void shouldRejectFailedDiscoveryCommand() {
        WindowsApplicationDiscovery discovery = new WindowsApplicationDiscovery(
                (command, timeout) -> new WindowsApplicationDiscovery.CommandResult(1, "", "denied"),
                new ObjectMapper());

        Exception error = assertThrows(Exception.class, discovery::discover);

        assertTrue(error.getMessage().contains("denied"));
    }

    private WindowsApplicationDiscovery.CommandResult successful(String json, List<String> command,
                                                                  Duration timeout) {
        assertEquals("powershell.exe", command.getFirst());
        assertEquals(Duration.ofSeconds(10), timeout);
        return new WindowsApplicationDiscovery.CommandResult(0, json, "");
    }
}

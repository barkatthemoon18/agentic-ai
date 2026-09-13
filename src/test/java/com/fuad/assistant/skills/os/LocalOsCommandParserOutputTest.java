package com.fuad.assistant.skills.os;

import com.fuad.enums.OsAction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LocalOsCommandParserOutputTest {
    @Test
    void shouldParseEverySupportedApplicationIntentWithoutExecutingModelText() {
        assertIntent("open_application|Firefox", OsAction.OPEN_APPLICATION, "Firefox");
        assertIntent("close_application|Spotify", OsAction.CLOSE_APPLICATION, "Spotify");
        assertIntent("focus_application|Visual Studio Code", OsAction.FOCUS_APPLICATION, "Visual Studio Code");
        assertIntent("list_applications|none", OsAction.LIST_APPLICATIONS, "");
        assertIntent("list_applications|Adobe", OsAction.LIST_APPLICATIONS, "Adobe");
        assertIntent("list_running_applications|none", OsAction.LIST_RUNNING_APPLICATIONS, "");
        assertIntent("check_application_installed|Firefox", OsAction.CHECK_APPLICATION_INSTALLED, "Firefox");
        assertIntent("get_application_status|Spotify", OsAction.GET_APPLICATION_STATUS, "Spotify");
        assertIntent("unsupported|unknown", OsAction.UNSUPPORTED, "");
    }

    @Test
    void aggregateOpenApplicationQuestionsShouldBypassModelInference() {
        LocalOsCommandParser parser = new LocalOsCommandParser(request -> {
            throw new AssertionError("Deterministic aggregate query must not invoke the model");
        });

        for (String query : java.util.List.of("Ares, ¿qué aplicaciones están abiertas?",
                "¿Cuáles apps tengo abiertas?", "qué programas están abiertos")) {
            OsCommandIntent intent = parser.parse(query);
            assertEquals(OsAction.LIST_RUNNING_APPLICATIONS, intent.getAction());
            assertEquals("", intent.getTarget());
        }
    }

    @Test
    void shouldRejectFreeTextAndShellOutput() {
        assertThrows(IllegalStateException.class,
                () -> LocalOsCommandParser.parseOutput("open_application|Spotify\nRemove-Item C:\\*"));
        assertThrows(IllegalStateException.class,
                () -> LocalOsCommandParser.parseOutput("run_shell|calc.exe"));
        assertThrows(IllegalStateException.class,
                () -> LocalOsCommandParser.parseOutput("list_running_applications|Piper"));
    }

    private void assertIntent(String output, OsAction action, String target) {
        OsCommandIntent intent = LocalOsCommandParser.parseOutput(output);
        assertEquals(action, intent.getAction());
        assertEquals(target, intent.getTarget());
    }
}

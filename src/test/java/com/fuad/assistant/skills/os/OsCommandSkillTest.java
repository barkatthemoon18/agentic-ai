package com.fuad.assistant.skills.os;

import com.fuad.assistant.AssistantResult;
import com.fuad.enums.OsAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OsCommandSkillTest {
    private ApplicationDefinition spotify;
    private TrackingController controller;

    @BeforeEach
    void setUp() {
        spotify = new ApplicationDefinition("spotify", "Spotify", List.of("spotify"), "Spotify.exe");
        controller = new TrackingController();
    }

    @Test
    void shouldRejectUnsafeCommandBeforeParsing() {
        TrackingParser parser = new TrackingParser(new OsCommandIntent(OsAction.CLOSE_APPLICATION, "spotify"));
        OsCommandSkill skill = skill(parser, guard(false));

        AssistantResult result = skill.execute("mañana cierra Spotify");

        assertEquals("No interpreté eso como una orden inmediata.", result.getText());
        assertFalse(parser.called);
        assertFalse(controller.called);
    }

    @Test
    void shouldReportUnsupportedIntent() {
        AssistantResult result = skill(command -> OsCommandIntent.unsupported(), guard(true))
                .execute("haz algo");

        assertEquals("Ese comando del sistema todavía no está soportado", result.getText());
    }

    @Test
    void shouldReportUnregisteredApplication() {
        AssistantResult result = skill(
                command -> new OsCommandIntent(OsAction.OPEN_APPLICATION, "firefox"), guard(true))
                .execute("abre Firefox");

        assertEquals("No tengo registrada esa aplicación", result.getText());
        assertFalse(controller.called);
    }

    @Test
    void shouldOpenRegisteredApplication() {
        AssistantResult result = skill(
                command -> new OsCommandIntent(OsAction.OPEN_APPLICATION, "spotify"), guard(true))
                .execute("abre Spotify");

        assertEquals("Abriendo: Spotify.", result.getText());
        assertSame(spotify, controller.application);
    }

    @Test
    void shouldCloseRunningApplication() {
        controller.closeResult = true;

        AssistantResult result = skill(
                command -> new OsCommandIntent(OsAction.CLOSE_APPLICATION, "spotify"), guard(true))
                .execute("cierra Spotify");

        assertEquals("Cerrando: Spotify.", result.getText());
    }

    @Test
    void shouldReportApplicationThatIsNotRunning() {
        controller.closeResult = false;

        AssistantResult result = skill(
                command -> new OsCommandIntent(OsAction.CLOSE_APPLICATION, "spotify"), guard(true))
                .execute("cierra Spotify");

        assertEquals("Spotify no está abierto", result.getText());
    }

    @Test
    void shouldConvertControllerFailureToSafeResponse() {
        controller.openFailure = new IOException("boom");

        AssistantResult result = skill(
                command -> new OsCommandIntent(OsAction.OPEN_APPLICATION, "spotify"), guard(true))
                .execute("abre Spotify");

        assertEquals("No pude ejecutar esa acción", result.getText());
    }

    @Test
    void shouldFallbackForKnownButUnimplementedAction() {
        AssistantResult result = skill(
                command -> new OsCommandIntent(OsAction.FOCUS_APPLICATION, "spotify"), guard(true))
                .execute("enfoca Spotify");

        assertEquals("Ese comando del sistema todavía no está soportado", result.getText());
    }

    private OsCommandSkill skill(OsCommandParser parser, OsCommandSafetyGuard guard) {
        return new OsCommandSkill(parser, new ApplicationRegistry(Map.of("spotify", spotify)), controller, guard);
    }

    private OsCommandSafetyGuard guard(boolean result) {
        return new OsCommandSafetyGuard() {
            @Override public boolean canExecute(String command) { return result; }
        };
    }

    private static final class TrackingParser implements OsCommandParser {
        private final OsCommandIntent result;
        private boolean called;

        private TrackingParser(OsCommandIntent result) { this.result = result; }

        @Override
        public OsCommandIntent parse(String command) {
            called = true;
            return result;
        }
    }

    private static final class TrackingController implements ApplicationController {
        private boolean called;
        private boolean closeResult;
        private ApplicationDefinition application;
        private IOException openFailure;

        @Override
        public boolean open(ApplicationDefinition applicationDefinition) throws IOException {
            called = true;
            application = applicationDefinition;
            if (openFailure != null) throw openFailure;
            return true;
        }

        @Override
        public boolean close(ApplicationDefinition applicationDefinition) {
            called = true;
            application = applicationDefinition;
            return closeResult;
        }
    }
}

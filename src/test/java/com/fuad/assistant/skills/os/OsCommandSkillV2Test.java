package com.fuad.assistant.skills.os;

import com.fuad.assistant.AssistantResult;
import com.fuad.assistant.session.ConversationSnapshot;
import com.fuad.enums.Capability;
import com.fuad.enums.OsAction;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OsCommandSkillV2Test {
    @Test
    void listAndVoiceFollowUpShouldUseTheSameCatalogSession() {
        CatalogSessionStore sessions = new CatalogSessionStore();
        Map<String, ApplicationDefinition> applications = new LinkedHashMap<>();
        for (int index = 0; index < 25; index++) {
            ApplicationDefinition app = new ApplicationDefinition("app-" + index,
                    "Application " + String.format("%02d", index), List.of("open"), "app.exe");
            applications.put(app.getId(), app);
        }
        OsCommandSkill skill = skill(command -> new OsCommandIntent(OsAction.LIST_APPLICATIONS, ""),
                applications, new TrackingController(), sessions);

        AssistantResult first = skill.execute("qué aplicaciones tengo");
        ApplicationCatalogPayload firstPayload = (ApplicationCatalogPayload) first.getPayload();
        ConversationSnapshot snapshot = new ConversationSnapshot(Capability.OS_COMMAND,
                "qué aplicaciones tengo", first.getText(), null, null, null, first.getOsConversationState());
        AssistantResult second = skill.executeFollowUp("siguiente", snapshot);

        assertEquals(0, firstPayload.pageIndex());
        assertEquals(1, ((ApplicationCatalogPayload) second.getPayload()).pageIndex());
        assertEquals(firstPayload.sessionId(), ((ApplicationCatalogPayload) second.getPayload()).sessionId());
    }

    @Test
    void shouldReportTypedRuntimeStates() {
        TrackingController controller = new TrackingController();
        controller.runtime = ApplicationActionResult.status(ApplicationRuntimeState.RUNNING_BACKGROUND);
        OsCommandSkill skill = skill(command -> new OsCommandIntent(OsAction.GET_APPLICATION_STATUS, "Spotify"),
                Map.of("spotify", spotify()), controller, new CatalogSessionStore());

        assertEquals("Spotify está ejecutándose en segundo plano, sin una ventana visible.",
                skill.execute("está Spotify abierto").getText());
    }

    @Test
    void shouldAnswerInstallationQueriesFromCatalog() {
        OsCommandSkill skill = skill(command -> new OsCommandIntent(OsAction.CHECK_APPLICATION_INSTALLED, "Spotify"),
                Map.of("spotify", spotify()), new TrackingController(), new CatalogSessionStore());

        assertEquals("Sí, Spotify está instalada.", skill.execute("está Spotify instalado").getText());
    }

    private OsCommandSkill skill(OsCommandParser parser, Map<String, ApplicationDefinition> applications,
                                 ApplicationController controller, CatalogSessionStore sessions) {
        return new OsCommandSkill(parser, new ApplicationRegistry(applications), controller,
                new OsCommandSafetyGuard(), sessions);
    }

    private ApplicationDefinition spotify() {
        return new ApplicationDefinition("spotify", "Spotify", List.of("open"), "Spotify.exe");
    }

    private static final class TrackingController implements ApplicationController {
        private ApplicationActionResult runtime = ApplicationActionResult.status(ApplicationRuntimeState.NOT_RUNNING);
        @Override public boolean open(ApplicationDefinition applicationDefinition) { return true; }
        @Override public boolean close(ApplicationDefinition applicationDefinition) { return true; }
        @Override public ApplicationActionResult runtimeState(ApplicationDefinition applicationDefinition) { return runtime; }
    }
}

package com.fuad.assistant.skills.os;

import com.fuad.assistant.AssistantResult;
import com.fuad.assistant.session.ConversationSnapshot;
import com.fuad.assistant.skills.SkillExecution;
import com.fuad.enums.Capability;
import com.fuad.enums.OsAction;
import com.fuad.interaction.InputModality;
import com.fuad.interaction.InteractionResult;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class OsCommandSkillV2Test {
    @Test
    void ambiguousOpenShouldSuspendAndResumeWithTheExactSelectedApplication() {
        ApplicationDefinition idea = new ApplicationDefinition("idea", "IntelliJ IDEA",
                Set.of("studio"), List.of("idea"), ApplicationProcessIdentity.empty());
        ApplicationDefinition code = new ApplicationDefinition("code", "Visual Studio Code",
                Set.of("studio"), List.of("code"), ApplicationProcessIdentity.empty());
        TrackingController controller = new TrackingController();
        OsCommandSkill skill = skill(
                command -> new OsCommandIntent(OsAction.OPEN_APPLICATION, "studio"),
                Map.of(idea.getId(), idea, code.getId(), code), controller,
                new CatalogSessionStore());

        SkillExecution execution = skill.executeTurn("abre studio");

        SkillExecution.AwaitingInteraction<?> awaiting =
                assertInstanceOf(SkillExecution.AwaitingInteraction.class, execution);
        @SuppressWarnings("unchecked")
        SkillExecution.AwaitingInteraction<String> typed =
                (SkillExecution.AwaitingInteraction<String>) awaiting;
        SkillExecution resumed = typed.continuation().apply(new InteractionResult<>(
                Optional.empty(), com.fuad.interaction.InteractionOutcome.SUBMITTED,
                Optional.of("code"), Optional.of(InputModality.TOUCH)));

        assertEquals("Abriendo: Visual Studio Code.",
                assertInstanceOf(SkillExecution.Completed.class, resumed).result().getText());
        assertEquals("code", controller.openedId);
    }

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

    @Test
    void shouldExposeFunctionalLimitationForAllRuntimeOperations() {
        TrackingController controller = new TrackingController();
        controller.limited = true;
        Map<String, ApplicationDefinition> applications = Map.of("spotify", spotify());

        AssistantResult close = skill(command -> new OsCommandIntent(OsAction.CLOSE_APPLICATION, "Spotify"),
                applications, controller, new CatalogSessionStore()).execute("cierra Spotify");
        AssistantResult focus = skill(command -> new OsCommandIntent(OsAction.FOCUS_APPLICATION, "Spotify"),
                applications, controller, new CatalogSessionStore()).execute("enfoca Spotify");
        AssistantResult status = skill(command -> new OsCommandIntent(OsAction.GET_APPLICATION_STATUS, "Spotify"),
                applications, controller, new CatalogSessionStore()).execute("está Spotify abierto");

        assertEquals("Puedo abrir Spotify, pero no identificar sus procesos con seguridad.", close.getText());
        assertEquals("No puedo identificar una ventana de Spotify con seguridad.", focus.getText());
        assertEquals("No puedo comprobar el estado de Spotify con seguridad.", status.getText());
    }

    @Test
    void shouldReturnVerifiedOpenApplicationsAndKeepUnverifiableOnesOutOfTheList() {
        TrackingController controller = new TrackingController();
        controller.openApplications = OpenApplicationsResult.success(List.of(
                new OpenApplicationsResult.Entry(spotify(), ApplicationRuntimeState.RUNNING_WITH_WINDOW)), 2);
        OsCommandSkill skill = skill(command -> new OsCommandIntent(OsAction.LIST_RUNNING_APPLICATIONS, ""),
                Map.of("spotify", spotify()), controller, new CatalogSessionStore());

        AssistantResult result = skill.execute("qué aplicaciones están abiertas");

        assertEquals("Tienes 1 aplicación abierta: Spotify. No pude verificar el estado de 2 aplicaciones más.",
                result.getText());
        OpenApplicationsPayload payload = (OpenApplicationsPayload) result.getPayload();
        assertEquals(List.of(new OpenApplicationItem("spotify", "Spotify")), payload.items());
        assertEquals(2, payload.unverifiableCount());
    }

    @Test
    void longOpenApplicationListShouldSpeakOnlyTheFirstFiveNames() {
        List<OpenApplicationsResult.Entry> entries = IntStream.rangeClosed(1, 6)
                .mapToObj(index -> new OpenApplicationsResult.Entry(
                        new ApplicationDefinition("app-" + index, "App " + index, List.of("open"), "app.exe"),
                        ApplicationRuntimeState.RUNNING_WITH_WINDOW)).toList();
        TrackingController controller = new TrackingController();
        controller.openApplications = OpenApplicationsResult.success(entries, 0);
        OsCommandSkill skill = skill(command -> new OsCommandIntent(OsAction.LIST_RUNNING_APPLICATIONS, ""),
                Map.of(), controller, new CatalogSessionStore());

        AssistantResult result = skill.execute("qué aplicaciones están abiertas");

        assertEquals("Tienes 6 aplicaciones abiertas. Entre ellas: App 1, App 2, App 3, App 4 y App 5. "
                + "Te muestro la lista completa en pantalla.", result.getText());
        assertEquals(6, ((OpenApplicationsPayload) result.getPayload()).items().size());
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
        private boolean limited;
        private OpenApplicationsResult openApplications = OpenApplicationsResult.success(List.of(), 0);
        private String openedId;
        @Override public boolean open(ApplicationDefinition applicationDefinition) {
            openedId = applicationDefinition.getId();
            return true;
        }
        @Override public boolean close(ApplicationDefinition applicationDefinition) { return true; }
        @Override public ApplicationActionResult closeDetailed(ApplicationDefinition applicationDefinition) {
            return limited ? ApplicationActionResult.of(ApplicationActionResult.Status.PROCESS_IDENTITY_UNAVAILABLE)
                    : ApplicationController.super.closeDetailed(applicationDefinition);
        }
        @Override public ApplicationActionResult focus(ApplicationDefinition applicationDefinition) {
            return limited ? ApplicationActionResult.of(ApplicationActionResult.Status.PROCESS_IDENTITY_UNAVAILABLE)
                    : ApplicationController.super.focus(applicationDefinition);
        }
        @Override public ApplicationActionResult runtimeState(ApplicationDefinition applicationDefinition) {
            return limited ? ApplicationActionResult.of(ApplicationActionResult.Status.PROCESS_IDENTITY_UNAVAILABLE)
                    : runtime;
        }
        @Override public OpenApplicationsResult runningApplications() { return openApplications; }
    }
}

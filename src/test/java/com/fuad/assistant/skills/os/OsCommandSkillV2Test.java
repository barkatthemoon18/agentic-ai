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
    void workspaceActionShouldResolveAndOpenWithoutCallingLanguageParser() {
        TrackingController controller = new TrackingController();
        OsCommandSkill skill = skill(command -> { throw new AssertionError("Workspace actions must bypass parsing"); },
                Map.of("spotify", spotify()), controller, new CatalogSessionStore());

        SkillExecution.Completed result = assertInstanceOf(SkillExecution.Completed.class,
                skill.executionAction(OsAction.OPEN_APPLICATION, "Spotify"));

        assertEquals("spotify", controller.openedId);
        assertEquals("Abriendo: Spotify.", result.result().text());
    }

    @Test
    void workspaceActionShouldStillRequireSelectionWhenTargetIsAmbiguous() {
        ApplicationDefinition idea = new ApplicationDefinition("idea", "IntelliJ IDEA", Set.of("studio"),
                List.of("idea"), ApplicationProcessIdentity.empty());
        ApplicationDefinition code = new ApplicationDefinition("code", "Visual Studio Code", Set.of("studio"),
                List.of("code"), ApplicationProcessIdentity.empty());
        TrackingController controller = new TrackingController();
        OsCommandSkill skill = skill(command -> { throw new AssertionError("Must bypass parsing"); },
                Map.of("idea", idea, "code", code), controller, new CatalogSessionStore());
        @SuppressWarnings("unchecked")
        SkillExecution.AwaitingInteraction<String> awaiting = (SkillExecution.AwaitingInteraction<String>)
                skill.executionAction(OsAction.OPEN_APPLICATION, "studio");
        assertNull(controller.openedId);
        SkillExecution.Completed result = assertInstanceOf(SkillExecution.Completed.class,
                awaiting.continuation().apply(InteractionResult.submitted(awaiting.request(), "code", InputModality.TOUCH)));
        assertEquals("code", controller.openedId);
        assertEquals("Abriendo: Visual Studio Code.", result.result().text());
    }

    @Test
    void invalidWorkspaceActionShouldNotCallController() {
        TrackingController controller = new TrackingController();
        OsCommandSkill skill = skill(command -> { throw new AssertionError("Must bypass parsing"); },
                Map.of(), controller, new CatalogSessionStore());
        assertThrows(NullPointerException.class, () -> skill.executionAction(null, "Spotify"));
        assertThrows(NullPointerException.class, () -> skill.executionAction(OsAction.OPEN_APPLICATION, null));
        assertInstanceOf(SkillExecution.Completed.class, skill.executionAction(OsAction.UNSUPPORTED, ""));
        assertNull(controller.selectedId);
    }

    @Test
    void ambiguousOpenShouldSuspendAndResumeWithTheExactSelectedApplication() {
        ApplicationDefinition idea = new ApplicationDefinition("idea", "IntelliJ IDEA",
                Set.of("studio"), List.of("idea"), ApplicationProcessIdentity.empty());
        ApplicationDefinition code = new ApplicationDefinition("code", "Visual Studio Code",
                Set.of("studio"), List.of("code"), ApplicationProcessIdentity.empty());
        TrackingController controller = new TrackingController();
        OsCommandSkill skill = skill(
                command -> new OsCommandIntent(OsAction.OPEN_APPLICATION, "studio"),
                Map.of(idea.id(), idea, code.id(), code), controller,
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
                assertInstanceOf(SkillExecution.Completed.class, resumed).result().text());
        assertEquals("code", controller.openedId);
    }

    @Test
    void everyTargetedActionShouldWaitForAUniqueCatalogSelection() {
        List<OsAction> actions = List.of(OsAction.OPEN_APPLICATION, OsAction.CLOSE_APPLICATION,
                OsAction.FOCUS_APPLICATION, OsAction.GET_APPLICATION_STATUS,
                OsAction.CHECK_APPLICATION_INSTALLED);
        for (OsAction action : actions) {
            ApplicationDefinition android = new ApplicationDefinition("android", "Android Studio",
                    Set.of(), List.of("android"), ApplicationProcessIdentity.empty());
            ApplicationDefinition visual = new ApplicationDefinition("visual", "Visual Studio Code",
                    Set.of(), List.of("visual"), ApplicationProcessIdentity.empty());
            TrackingController controller = new TrackingController();
            controller.runtime = ApplicationActionResult.status(ApplicationRuntimeState.RUNNING_WITH_WINDOW);
            OsCommandSkill skill = skill(command -> new OsCommandIntent(action, "studio"),
                    Map.of(android.id(), android, visual.id(), visual), controller,
                    new CatalogSessionStore());

            SkillExecution execution = skill.executeTurn("comando studio");
            SkillExecution.AwaitingInteraction<?> awaiting =
                    assertInstanceOf(SkillExecution.AwaitingInteraction.class, execution, action.name());
            @SuppressWarnings("unchecked")
            SkillExecution.AwaitingInteraction<String> typed =
                    (SkillExecution.AwaitingInteraction<String>) awaiting;

            SkillExecution resumed = typed.continuation().apply(new InteractionResult<>(
                    Optional.empty(), com.fuad.interaction.InteractionOutcome.SUBMITTED,
                    Optional.of("android"), Optional.of(InputModality.TOUCH)));

            assertInstanceOf(SkillExecution.Completed.class, resumed, action.name());
            if (action == OsAction.CHECK_APPLICATION_INSTALLED) {
                assertNull(controller.selectedId, action.name());
                assertTrue(((SkillExecution.Completed) resumed).result().text()
                        .contains("Android Studio"), action.name());
            }
            else {
                assertEquals("android", controller.selectedId, action.name());
            }
        }
    }

    @Test
    void terminalOrInvalidSelectionShouldNeverReachController() {
        ApplicationDefinition android = new ApplicationDefinition("android", "Android Studio",
                Set.of(), List.of("android"), ApplicationProcessIdentity.empty());
        ApplicationDefinition visual = new ApplicationDefinition("visual", "Visual Studio Code",
                Set.of(), List.of("visual"), ApplicationProcessIdentity.empty());
        TrackingController controller = new TrackingController();
        OsCommandSkill skill = skill(
                command -> new OsCommandIntent(OsAction.CLOSE_APPLICATION, "studio"),
                Map.of(android.id(), android, visual.id(), visual), controller,
                new CatalogSessionStore());
        @SuppressWarnings("unchecked")
        SkillExecution.AwaitingInteraction<String> awaiting =
                (SkillExecution.AwaitingInteraction<String>) skill.executeTurn("cierra studio");

        SkillExecution invalid = awaiting.continuation().apply(new InteractionResult<>(
                Optional.empty(), com.fuad.interaction.InteractionOutcome.SUBMITTED,
                Optional.of("outside"), Optional.of(InputModality.TOUCH)));
        assertEquals("La selección de aplicación ya no es válida.",
                assertInstanceOf(SkillExecution.Completed.class, invalid).result().text());
        assertNull(controller.selectedId);

        SkillExecution cancelled = awaiting.continuation().apply(new InteractionResult<>(
                Optional.empty(), com.fuad.interaction.InteractionOutcome.CANCELLED,
                Optional.empty(), Optional.of(InputModality.VOICE)));
        assertEquals("Acción cancelada.",
                assertInstanceOf(SkillExecution.Completed.class, cancelled).result().text());
        assertNull(controller.selectedId);
    }

    @Test
    void legitimateRestartWhileSelectorIsOpenShouldUseThePostSelectionInstance() {
        ApplicationDefinition android = new ApplicationDefinition("android", "Android Studio",
                Set.of(), List.of("android"), ApplicationProcessIdentity.empty());
        ApplicationDefinition visual = new ApplicationDefinition("visual", "Visual Studio Code",
                Set.of(), List.of("visual"), ApplicationProcessIdentity.empty());
        TrackingController controller = new TrackingController();
        controller.currentPid = 100L;
        OsCommandSkill skill = skill(
                command -> new OsCommandIntent(OsAction.CLOSE_APPLICATION, "studio"),
                Map.of(android.id(), android, visual.id(), visual), controller,
                new CatalogSessionStore());

        @SuppressWarnings("unchecked")
        SkillExecution.AwaitingInteraction<String> awaiting =
                (SkillExecution.AwaitingInteraction<String>) skill.executeTurn("cierra studio");
        assertNull(controller.observedPid);

        controller.currentPid = 200L;
        SkillExecution resumed = awaiting.continuation().apply(new InteractionResult<>(
                Optional.empty(), com.fuad.interaction.InteractionOutcome.SUBMITTED,
                Optional.of("android"), Optional.of(InputModality.TOUCH)));

        assertEquals("Cerrando: Android Studio.",
                assertInstanceOf(SkillExecution.Completed.class, resumed).result().text());
        assertEquals(200L, controller.observedPid);
    }

    @Test
    void listAndVoiceFollowUpShouldUseTheSameCatalogSession() {
        CatalogSessionStore sessions = new CatalogSessionStore();
        Map<String, ApplicationDefinition> applications = new LinkedHashMap<>();
        for (int index = 0; index < 25; index++) {
            ApplicationDefinition app = new ApplicationDefinition("app-" + index,
                    "Application " + String.format("%02d", index), List.of("open"), "app.exe");
            applications.put(app.id(), app);
        }
        OsCommandSkill skill = skill(command -> new OsCommandIntent(OsAction.LIST_APPLICATIONS, ""),
                applications, new TrackingController(), sessions);

        AssistantResult first = skill.execute("qué aplicaciones tengo");
        ApplicationCatalogPayload firstPayload = (ApplicationCatalogPayload) first.payload();
        ConversationSnapshot snapshot = new ConversationSnapshot(Capability.OS_COMMAND,
                "qué aplicaciones tengo", first.text(), null, null, null, first.osConversationState());
        AssistantResult second = skill.executeFollowUp("siguiente", snapshot);

        assertEquals(0, firstPayload.pageIndex());
        assertEquals(1, ((ApplicationCatalogPayload) second.payload()).pageIndex());
        assertEquals(firstPayload.sessionId(), ((ApplicationCatalogPayload) second.payload()).sessionId());
    }

    @Test
    void shouldReportTypedRuntimeStates() {
        TrackingController controller = new TrackingController();
        controller.runtime = ApplicationActionResult.status(ApplicationRuntimeState.RUNNING_BACKGROUND);
        OsCommandSkill skill = skill(command -> new OsCommandIntent(OsAction.GET_APPLICATION_STATUS, "Spotify"),
                Map.of("spotify", spotify()), controller, new CatalogSessionStore());

        assertEquals("Spotify está ejecutándose en segundo plano, sin una ventana visible.",
                skill.execute("está Spotify abierto").text());
    }

    @Test
    void shouldAnswerInstallationQueriesFromCatalog() {
        OsCommandSkill skill = skill(command -> new OsCommandIntent(OsAction.CHECK_APPLICATION_INSTALLED, "Spotify"),
                Map.of("spotify", spotify()), new TrackingController(), new CatalogSessionStore());

        assertEquals("Sí, Spotify está instalada.", skill.execute("está Spotify instalado").text());
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

        assertEquals("Puedo abrir Spotify, pero no identificar sus procesos con seguridad.", close.text());
        assertEquals("No puedo identificar una ventana de Spotify con seguridad.", focus.text());
        assertEquals("No puedo comprobar el estado de Spotify con seguridad.", status.text());
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
                result.text());
        OpenApplicationsPayload payload = (OpenApplicationsPayload) result.payload();
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
                + "Te muestro la lista completa en pantalla.", result.text());
        assertEquals(6, ((OpenApplicationsPayload) result.payload()).items().size());
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
        private String selectedId;
        private Long currentPid;
        private Long observedPid;
        @Override public boolean open(ApplicationDefinition applicationDefinition) {
            openedId = applicationDefinition.id();
            selectedId = applicationDefinition.id();
            return true;
        }
        @Override public boolean close(ApplicationDefinition applicationDefinition) {
            selectedId = applicationDefinition.id();
            return true;
        }
        @Override public ApplicationActionResult closeDetailed(ApplicationDefinition applicationDefinition) {
            if (currentPid != null) {
                observedPid = currentPid;
                selectedId = applicationDefinition.id();
                return ApplicationActionResult.success();
            }
            return limited ? ApplicationActionResult.of(ApplicationActionResult.Status.PROCESS_IDENTITY_UNAVAILABLE)
                    : ApplicationController.super.closeDetailed(applicationDefinition);
        }
        @Override public ApplicationActionResult focus(ApplicationDefinition applicationDefinition) {
            selectedId = applicationDefinition.id();
            return limited ? ApplicationActionResult.of(ApplicationActionResult.Status.PROCESS_IDENTITY_UNAVAILABLE)
                    : ApplicationController.super.focus(applicationDefinition);
        }
        @Override public ApplicationActionResult runtimeState(ApplicationDefinition applicationDefinition) {
            selectedId = applicationDefinition.id();
            return limited ? ApplicationActionResult.of(ApplicationActionResult.Status.PROCESS_IDENTITY_UNAVAILABLE)
                    : runtime;
        }
        @Override public OpenApplicationsResult runningApplications() { return openApplications; }
    }
}

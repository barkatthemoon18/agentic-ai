package com.fuad.assistant.skills.os;

import com.fuad.assistant.AssistantResult;
import com.fuad.assistant.session.ConversationSnapshot;
import com.fuad.assistant.skills.Skill;
import com.fuad.assistant.skills.SkillExecution;
import com.fuad.enums.OsAction;
import com.fuad.interaction.ChoiceOption;
import com.fuad.interaction.ChoiceRequest;
import com.fuad.interaction.ChoiceVoiceResolution;
import com.fuad.interaction.FocusRequirement;
import com.fuad.interaction.InputModality;
import com.fuad.interaction.InteractionOutcome;
import com.fuad.interaction.InteractionResult;

import java.io.IOException;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OsCommandSkill implements Skill {
    private static final Pattern FILTER_FOLLOW_UP = Pattern.compile(
            "^(?:muestra|muestrame|busca|filtra)(?:me)?(?: las)?(?: aplicaciones| apps)?(?: de| por)?\\s+(.+)$");
    private final OsCommandParser parser;
    private final ApplicationRegistry applicationRegistry;
    private final ApplicationController applicationController;
    private final OsCommandSafetyGuard safetyGuard;
    private final CatalogSessionStore catalogSessions;

    public OsCommandSkill(OsCommandParser parser, ApplicationRegistry applicationRegistry,
                          ApplicationController applicationController, OsCommandSafetyGuard safetyGuard) {
        this(parser, applicationRegistry, applicationController, safetyGuard, new CatalogSessionStore());
    }

    public OsCommandSkill(OsCommandParser parser, ApplicationRegistry applicationRegistry,
                          ApplicationController applicationController, OsCommandSafetyGuard safetyGuard,
                          CatalogSessionStore catalogSessions) {
        this.parser = parser;
        this.applicationRegistry = applicationRegistry;
        this.applicationController = applicationController;
        this.safetyGuard = safetyGuard;
        this.catalogSessions = catalogSessions;
    }

    @Override
    public AssistantResult execute(String command) {
        if (!safetyGuard.canExecute(command)) {
            System.out.println("OS SAFETY -> REJECTED");
            return new AssistantResult("No interpreté eso como una orden inmediata.");
        }
        OsCommandIntent intent;
        try {
            intent = parser.parse(command);
        }
        catch (RuntimeException e) {
            System.err.println("OS command parsing failed: " + e.getMessage());
            return new AssistantResult("No pude interpretar el comando del sistema.");
        }
        if (intent.getAction() == OsAction.UNSUPPORTED) {
            return new AssistantResult("Ese comando del sistema todavía no está soportado");
        }
        try {
            return executeParsed(intent);
        }
        catch (Exception e) {
            System.err.println("OS command failed: " + e.getMessage());
            return new AssistantResult("No pude ejecutar esa acción");
        }
    }

    @Override
    public SkillExecution executeTurn(String command) {
        if (!safetyGuard.canExecute(command)) {
            System.out.println("OS SAFETY -> REJECTED");
            return SkillExecution.completed(new AssistantResult(
                    "No interpreté eso como una orden inmediata."));
        }
        OsCommandIntent intent;
        try {
            intent = parser.parse(command);
        }
        catch (RuntimeException e) {
            System.err.println("OS command parsing failed: " + e.getMessage());
            return SkillExecution.completed(new AssistantResult(
                    "No pude interpretar el comando del sistema."));
        }
        if (intent.getAction() == OsAction.UNSUPPORTED) {
            return SkillExecution.completed(new AssistantResult(
                    "Ese comando del sistema todavía no está soportado"));
        }
        try {
            return requiresUniqueApplication(intent.getAction())
                    ? executeInteractively(intent)
                    : SkillExecution.completed(executeParsed(intent));
        }
        catch (Exception e) {
            System.err.println("OS command failed: " + e.getMessage());
            return SkillExecution.completed(new AssistantResult("No pude ejecutar esa acción"));
        }
    }

    @Override
    public AssistantResult executeFollowUp(String command, ConversationSnapshot snapshot) {
        if (snapshot == null || snapshot.getOsConversationState() == null) return execute(command);
        UUID sessionId = snapshot.getOsConversationState().catalogSessionId();
        String normalized = normalize(command);
        CatalogNavigation navigation = switch (normalized) {
            case "siguiente", "siguiente pagina", "pagina siguiente", "muestrame mas", "muestra mas", "continua" -> CatalogNavigation.NEXT;
            case "anterior", "pagina anterior", "atras" -> CatalogNavigation.PREVIOUS;
            case "primera", "primera pagina", "al principio" -> CatalogNavigation.FIRST;
            case "ultima", "ultima pagina", "al final" -> CatalogNavigation.LAST;
            default -> null;
        };
        if (navigation != null) {
            return catalogSessions.navigate(sessionId, navigation).map(this::catalogResult)
                    .orElseGet(() -> new AssistantResult("La sesión del catálogo ya no está disponible."));
        }
        Matcher filter = FILTER_FOLLOW_UP.matcher(normalized);
        if (filter.matches()) {
            return catalogSessions.filter(sessionId, filter.group(1)).map(this::catalogResult)
                    .orElseGet(() -> new AssistantResult("La sesión del catálogo ya no está disponible."));
        }
        return execute(command);
    }

    @Override
    public SkillExecution executeFollowUpTurn(String command, ConversationSnapshot snapshot) {
        if (snapshot == null || snapshot.getOsConversationState() == null) {
            return executeTurn(command);
        }
        UUID sessionId = snapshot.getOsConversationState().catalogSessionId();
        String normalized = normalize(command);
        CatalogNavigation navigation = navigation(normalized);
        if (navigation != null) {
            return SkillExecution.completed(catalogSessions.navigate(sessionId, navigation)
                    .map(this::catalogResult)
                    .orElseGet(() -> new AssistantResult(
                            "La sesión del catálogo ya no está disponible.")));
        }
        Matcher filter = FILTER_FOLLOW_UP.matcher(normalized);
        if (filter.matches()) {
            return SkillExecution.completed(catalogSessions.filter(sessionId, filter.group(1))
                    .map(this::catalogResult)
                    .orElseGet(() -> new AssistantResult(
                            "La sesión del catálogo ya no está disponible.")));
        }
        return executeTurn(command);
    }

    private SkillExecution executeInteractively(OsCommandIntent intent) throws IOException {
        ApplicationResolution resolution = applicationRegistry.resolve(intent.getTarget(), true);
        if (resolution.status() == ApplicationResolution.Status.CATALOG_UNAVAILABLE) {
            return SkillExecution.completed(new AssistantResult(
                    "El catálogo de aplicaciones no está disponible en este momento."));
        }
        if (resolution.status() != ApplicationResolution.Status.AMBIGUOUS) {
            ApplicationDefinition application = resolution.found().orElse(null);
            return SkillExecution.completed(application == null
                    ? unknown(intent)
                    : executeSelected(intent, application));
        }

        List<ApplicationDefinition> candidates = List.copyOf(resolution.candidates());
        List<ChoiceOption> options = candidates.stream()
                .map(application -> new ChoiceOption(choiceId(application),
                        application.getDisplayName(), List.copyOf(application.getAliases())))
                .toList();
        ChoiceRequest request = new ChoiceRequest(Optional.empty(),
                ambiguityPrompt(intent.getAction()),
                Set.of(InputModality.TOUCH, InputModality.VOICE), Optional.empty(),
                FocusRequirement.PASSIVE, options, Optional.of(transcription -> {
                    ApplicationResolution voice = applicationRegistry.resolveAmong(transcription, candidates);
                    return switch (voice.status()) {
                        case FOUND -> ChoiceVoiceResolution.resolved(
                                choiceId(voice.found().orElseThrow()));
                        case AMBIGUOUS -> ChoiceVoiceResolution.ambiguous();
                        case UNKNOWN, CATALOG_UNAVAILABLE -> ChoiceVoiceResolution.unknown();
                    };
                }));
        return new SkillExecution.AwaitingInteraction<>(request,
                result -> SkillExecution.completed(resumeAmbiguous(intent, candidates, result)));
    }

    private AssistantResult resumeAmbiguous(OsCommandIntent intent,
                                            List<ApplicationDefinition> candidates,
                                            InteractionResult<String> result) {
        if (result.outcome() != InteractionOutcome.SUBMITTED) {
            return switch (result.outcome()) {
                case CANCELLED -> new AssistantResult("Acción cancelada.");
                case EXPIRED -> new AssistantResult("La selección de aplicación expiró.");
                case BUSY -> new AssistantResult("Ya hay otra interacción pendiente.");
                case UNAVAILABLE -> new AssistantResult(
                        "Encontré varias aplicaciones, pero la pantalla de interacción no está disponible.");
                case CLOSED -> new AssistantResult("La interacción ya no está disponible.");
                case SUBMITTED -> throw new IllegalStateException("unreachable");
            };
        }
        String selectedId = result.value().orElse("");
        ApplicationDefinition selected = candidates.stream()
                .filter(application -> choiceId(application).equals(selectedId))
                .findFirst().orElse(null);
        if (selected == null) {
            return new AssistantResult("La selección de aplicación ya no es válida.");
        }
        try {
            return executeSelected(intent, selected);
        }
        catch (IOException e) {
            System.err.println("OS command continuation failed: " + e.getMessage());
            return new AssistantResult("No pude ejecutar esa acción");
        }
    }

    private AssistantResult executeResolved(OsCommandIntent intent) throws IOException {
        ApplicationResolution resolution = applicationRegistry.resolve(intent.getTarget(), true);
        if (resolution.status() == ApplicationResolution.Status.CATALOG_UNAVAILABLE) {
            return new AssistantResult("El catálogo de aplicaciones no está disponible en este momento.");
        }
        if (resolution.status() == ApplicationResolution.Status.AMBIGUOUS) {
            return ambiguous(resolution);
        }
        ApplicationDefinition application = resolution.found().orElse(null);
        if (application == null) return unknown(intent);
        return executeSelected(intent, application);
    }

    private AssistantResult executeSelected(OsCommandIntent intent,
                                            ApplicationDefinition application) throws IOException {
        return switch (intent.getAction()) {
            case OPEN_APPLICATION -> open(application);
            case CLOSE_APPLICATION -> close(application);
            case FOCUS_APPLICATION -> focus(application);
            case GET_APPLICATION_STATUS -> status(application);
            case CHECK_APPLICATION_INSTALLED -> installed(application);
            default -> new AssistantResult("Ese comando del sistema todavía no está soportado");
        };
    }

    private AssistantResult executeParsed(OsCommandIntent intent) throws IOException {
        return switch (intent.getAction()) {
            case LIST_APPLICATIONS -> list(intent.getTarget());
            case LIST_RUNNING_APPLICATIONS -> listRunning();
            case OPEN_APPLICATION, CLOSE_APPLICATION, FOCUS_APPLICATION,
                 GET_APPLICATION_STATUS, CHECK_APPLICATION_INSTALLED -> executeResolved(intent);
            default -> new AssistantResult("Ese comando del sistema todavía no está soportado");
        };
    }

    private boolean requiresUniqueApplication(OsAction action) {
        return switch (action) {
            case OPEN_APPLICATION, CLOSE_APPLICATION, FOCUS_APPLICATION,
                 GET_APPLICATION_STATUS, CHECK_APPLICATION_INSTALLED -> true;
            default -> false;
        };
    }

    private String choiceId(ApplicationDefinition application) {
        String id = application.getId();
        return id == null || id.isBlank() ? applicationRegistry.catalogKey(application) : id;
    }

    private String ambiguityPrompt(OsAction action) {
        return switch (action) {
            case OPEN_APPLICATION -> "Encontré varias aplicaciones. ¿Cuál quieres abrir?";
            case CLOSE_APPLICATION -> "Encontré varias aplicaciones. ¿Cuál quieres cerrar?";
            case FOCUS_APPLICATION -> "Encontré varias aplicaciones. ¿Cuál quieres enfocar?";
            case GET_APPLICATION_STATUS -> "Encontré varias aplicaciones. ¿De cuál quieres consultar el estado?";
            case CHECK_APPLICATION_INSTALLED -> "Encontré varias aplicaciones. ¿Cuál quieres comprobar?";
            default -> "Encontré varias aplicaciones. ¿Cuál quieres elegir?";
        };
    }

    private AssistantResult unknown(OsCommandIntent intent) {
        return intent.getAction() == OsAction.CHECK_APPLICATION_INSTALLED
                ? new AssistantResult("No encontré " + intent.getTarget()
                + " entre las aplicaciones instaladas.")
                : new AssistantResult("No tengo registrada esa aplicación");
    }

    private CatalogNavigation navigation(String normalized) {
        return switch (normalized) {
            case "siguiente", "siguiente pagina", "pagina siguiente", "muestrame mas", "muestra mas", "continua" -> CatalogNavigation.NEXT;
            case "anterior", "pagina anterior", "atras" -> CatalogNavigation.PREVIOUS;
            case "primera", "primera pagina", "al principio" -> CatalogNavigation.FIRST;
            case "ultima", "ultima pagina", "al final" -> CatalogNavigation.LAST;
            default -> null;
        };
    }

    private AssistantResult open(ApplicationDefinition application) throws IOException {
        ApplicationActionResult result = applicationController.openDetailed(application);
        return result.status() == ApplicationActionResult.Status.SUCCESS
                ? new AssistantResult("Abriendo: " + application.getDisplayName() + ".")
                : new AssistantResult("No pude abrir " + application.getDisplayName() + ".");
    }

    private AssistantResult close(ApplicationDefinition application) {
        ApplicationActionResult result = applicationController.closeDetailed(application);
        return switch (result.status()) {
            case SUCCESS -> new AssistantResult("Cerrando: " + application.getDisplayName() + ".");
            case NOT_RUNNING -> new AssistantResult(application.getDisplayName() + " no está abierto");
            case PROCESS_IDENTITY_UNAVAILABLE -> new AssistantResult(
                    "Puedo abrir " + application.getDisplayName() + ", pero no identificar sus procesos con seguridad.");
            default -> new AssistantResult("No pude cerrar " + application.getDisplayName() + ".");
        };
    }

    private AssistantResult focus(ApplicationDefinition application) {
        ApplicationActionResult result = applicationController.focus(application);
        return switch (result.status()) {
            case SUCCESS -> new AssistantResult("Enfocando: " + application.getDisplayName() + ".");
            case NOT_RUNNING -> new AssistantResult(application.getDisplayName() + " no está abierto.");
            case NO_VISIBLE_WINDOW -> new AssistantResult(application.getDisplayName() + " no tiene una ventana visible.");
            case FOCUS_REJECTED -> new AssistantResult("Windows no permitió enfocar " + application.getDisplayName() + ".");
            case PROCESS_IDENTITY_UNAVAILABLE -> new AssistantResult(
                    "No puedo identificar una ventana de " + application.getDisplayName() + " con seguridad.");
            default -> new AssistantResult("No pude enfocar " + application.getDisplayName() + ".");
        };
    }

    private AssistantResult status(ApplicationDefinition application) {
        ApplicationActionResult result = applicationController.runtimeState(application);
        if (result.status() == ApplicationActionResult.Status.PROCESS_IDENTITY_UNAVAILABLE) {
            return new AssistantResult("No puedo comprobar el estado de " + application.getDisplayName() + " con seguridad.");
        }
        if (result.status() != ApplicationActionResult.Status.SUCCESS || result.runtimeState() == null) {
            return new AssistantResult("No pude comprobar el estado de " + application.getDisplayName() + ".");
        }
        return switch (result.runtimeState()) {
            case NOT_RUNNING -> new AssistantResult(application.getDisplayName() + " no está ejecutándose.");
            case RUNNING_BACKGROUND -> new AssistantResult(application.getDisplayName()
                    + " está ejecutándose en segundo plano, sin una ventana visible.");
            case RUNNING_WITH_WINDOW -> new AssistantResult(application.getDisplayName() + " está abierto.");
        };
    }

    private AssistantResult installed(String target) {
        ApplicationResolution resolution = applicationRegistry.resolve(target, true);
        if (resolution.status() == ApplicationResolution.Status.CATALOG_UNAVAILABLE) {
            return new AssistantResult("El catálogo de aplicaciones no está disponible en este momento.");
        }
        if (resolution.status() == ApplicationResolution.Status.AMBIGUOUS) {
            return ambiguous(resolution);
        }
        return resolution.found()
                .map(app -> new AssistantResult("Sí, " + app.getDisplayName() + " está instalada."))
                .orElseGet(() -> new AssistantResult("No encontré " + target + " entre las aplicaciones instaladas."));
    }

    private AssistantResult installed(ApplicationDefinition application) {
        return new AssistantResult("Sí, " + application.getDisplayName() + " está instalada.");
    }

    private AssistantResult ambiguous(ApplicationResolution resolution) {
        String choices = resolution.candidates().stream().limit(3)
                .map(ApplicationDefinition::getDisplayName).reduce((a, b) -> a + ", " + b).orElse("");
        return new AssistantResult("Encontré varias aplicaciones con ese nombre: " + choices + ".");
    }

    private AssistantResult list(String filter) {
        List<ApplicationDefinition> applications = applicationRegistry.search("", true);
        if (!applicationRegistry.isAvailable()) {
            return new AssistantResult("El catálogo de aplicaciones no está disponible en este momento.");
        }
        return catalogResult(catalogSessions.create(applications, filter,
                applicationRegistry::normalizeTarget));
    }

    private AssistantResult catalogResult(ApplicationCatalogPayload payload) {
        String text = payload.filter().isBlank()
                ? "Encontré " + payload.totalCount() + " aplicaciones; te las muestro en pantalla."
                : "Encontré " + payload.totalCount() + " aplicaciones para " + payload.filter()
                        + "; te las muestro en pantalla.";
        return AssistantResult.catalog(text, payload);
    }

    private AssistantResult listRunning() {
        OpenApplicationsResult result = applicationController.runningApplications();
        if (result.status() != OpenApplicationsResult.Status.SUCCESS) {
            return new AssistantResult("No pude comprobar qué aplicaciones están abiertas con seguridad.");
        }
        List<OpenApplicationItem> items = result.applications().stream()
                .map(entry -> new OpenApplicationItem(entry.application().getId(),
                        entry.application().getDisplayName())).toList();
        String text = runningApplicationsSpeech(items, result.unverifiableCount());
        return AssistantResult.openApplications(text,
                new OpenApplicationsPayload(items, result.unverifiableCount()));
    }

    static String runningApplicationsSpeech(List<OpenApplicationItem> items, int unverifiableCount) {
        String text;
        if (items.isEmpty()) {
            text = unverifiableCount == 0
                    ? "No encontré aplicaciones abiertas."
                    : "No identifiqué aplicaciones abiertas con seguridad.";
        }
        else if (items.size() <= 5) {
            text = "Tienes " + items.size() + (items.size() == 1
                    ? " aplicación abierta: " : " aplicaciones abiertas: ")
                    + naturalList(items.stream().map(OpenApplicationItem::displayName).toList()) + ".";
        }
        else {
            text = "Tienes " + items.size() + " aplicaciones abiertas. Entre ellas: "
                    + naturalList(items.stream().limit(5).map(OpenApplicationItem::displayName).toList())
                    + ". Te muestro la lista completa en pantalla.";
        }
        if (unverifiableCount > 0) {
            text += " No pude verificar el estado de " + unverifiableCount
                    + (unverifiableCount == 1 ? " aplicación más." : " aplicaciones más.");
        }
        return text;
    }

    private static String naturalList(List<String> names) {
        if (names.isEmpty()) return "";
        if (names.size() == 1) return names.getFirst();
        return String.join(", ", names.subList(0, names.size() - 1)) + " y " + names.getLast();
    }

    private String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[¿?¡!.,]", "").trim().replaceAll("\\s+", " ");
    }
}

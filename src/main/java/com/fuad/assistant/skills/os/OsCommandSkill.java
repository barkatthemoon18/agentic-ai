package com.fuad.assistant.skills.os;

import com.fuad.assistant.AssistantResult;
import com.fuad.assistant.session.ConversationSnapshot;
import com.fuad.assistant.skills.Skill;
import com.fuad.enums.OsAction;

import java.io.IOException;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
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
        OsCommandIntent intent = parser.parse(command);
        if (intent.getAction() == OsAction.UNSUPPORTED) {
            return new AssistantResult("Ese comando del sistema todavía no está soportado");
        }
        try {
            return switch (intent.getAction()) {
                case LIST_APPLICATIONS -> list(intent.getTarget());
                case CHECK_APPLICATION_INSTALLED -> installed(intent.getTarget());
                case OPEN_APPLICATION, CLOSE_APPLICATION, FOCUS_APPLICATION, GET_APPLICATION_STATUS ->
                        executeResolved(intent);
                default -> new AssistantResult("Ese comando del sistema todavía no está soportado");
            };
        }
        catch (Exception e) {
            System.err.println("OS command failed: " + e.getMessage());
            return new AssistantResult("No pude ejecutar esa acción");
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

    private AssistantResult executeResolved(OsCommandIntent intent) throws IOException {
        ApplicationResolution resolution = applicationRegistry.resolve(intent.getTarget(), true);
        if (resolution.status() == ApplicationResolution.Status.CATALOG_UNAVAILABLE) {
            return new AssistantResult("El catálogo de aplicaciones no está disponible en este momento.");
        }
        if (resolution.status() == ApplicationResolution.Status.AMBIGUOUS) {
            String choices = resolution.candidates().stream().limit(3)
                    .map(ApplicationDefinition::getDisplayName).reduce((a, b) -> a + ", " + b).orElse("");
            return new AssistantResult("Encontré varias aplicaciones con ese nombre: " + choices + ".");
        }
        ApplicationDefinition application = resolution.found().orElse(null);
        if (application == null) return new AssistantResult("No tengo registrada esa aplicación");
        return switch (intent.getAction()) {
            case OPEN_APPLICATION -> open(application);
            case CLOSE_APPLICATION -> close(application);
            case FOCUS_APPLICATION -> focus(application);
            case GET_APPLICATION_STATUS -> status(application);
            default -> new AssistantResult("Ese comando del sistema todavía no está soportado");
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
            return new AssistantResult("Ese nombre corresponde a varias aplicaciones; usa un nombre más específico.");
        }
        return resolution.found()
                .map(app -> new AssistantResult("Sí, " + app.getDisplayName() + " está instalada."))
                .orElseGet(() -> new AssistantResult("No encontré " + target + " entre las aplicaciones instaladas."));
    }

    private AssistantResult list(String filter) {
        List<ApplicationDefinition> applications = applicationRegistry.search("", true);
        if (!applicationRegistry.isAvailable()) {
            return new AssistantResult("El catálogo de aplicaciones no está disponible en este momento.");
        }
        return catalogResult(catalogSessions.create(applications, filter));
    }

    private AssistantResult catalogResult(ApplicationCatalogPayload payload) {
        String text = payload.filter().isBlank()
                ? "Encontré " + payload.totalCount() + " aplicaciones; te las muestro en pantalla."
                : "Encontré " + payload.totalCount() + " aplicaciones para " + payload.filter()
                        + "; te las muestro en pantalla.";
        return AssistantResult.catalog(text, payload);
    }

    private String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[¿?¡!.,]", "").trim().replaceAll("\\s+", " ");
    }
}

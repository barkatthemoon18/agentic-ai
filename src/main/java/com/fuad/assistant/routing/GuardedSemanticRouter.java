package com.fuad.assistant.routing;

import com.fuad.enums.Capability;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public class GuardedSemanticRouter implements SemanticRouter {
    private static final Pattern CURRENT_TIME_REQUEST = Pattern.compile(
            "^(?:que hora es|dime la hora|me dices la hora|en que fecha estamos"
                    + "|que fecha es hoy|que dia es hoy|en que dia estamos)[?.!]*$");
    private static final Pattern AUDIO_MUTATION = Pattern.compile(
            "^(?:pon|ajusta|cambia|sube|baja|aumenta|reduce|silencia|mutea|desmutea"
                    + "|quita el silencio|vuelve a hablar|habla mas fuerte|habla mas bajo)\\b.*$");
    private static final Pattern APPLICATION_LIFECYCLE = Pattern.compile(
            "^(?:(?:abre|abrir|cierra|cerrar|inicia|termina|reinicia|ejecuta)"
                    + "|(?:puedes|podrias) (?:abrir|cerrar|iniciar|terminar|reiniciar|ejecutar)"
                    + "|quiero que (?:abras|cierres|inicies|termines|reinicies|ejecutes))\\b.*$");
    private static final Pattern LOCAL_QUERY = Pattern.compile(
            "^¿?.*\\b(?:instalad[oa]|abiert[oa]|ejecutandose|procesos|archivo|directorio)\\b.*$");
    private static final Pattern CURRENT_RESEARCH_REQUEST = Pattern.compile(
            "^(?:.*\\b(?:ultima|ultimas|reciente|recientes)\\b.*\\b(?:version|release|noticia|noticias)\\b.*"
                    + "|.*\\b(?:version|release|noticia|noticias)\\b.*\\b(?:ultima|ultimas|reciente|recientes)\\b.*"
                    + "|.*\\bprecio actual\\b.*|¿?que ocurrio hoy\\b.*)$");

    private static final Pattern EXPLICIT_RESEARCH_REQUEST = Pattern.compile(
            ".*\\b(?:busca|buscar|buscame|buscalo|buscala|investiga|investigar)\\b.*");
    private static final Pattern FRESH_INFORMATION_REQUEST = Pattern.compile(
            ".*\\b(?:hoy|actual|actualmente|reciente|recientes|ultima|ultimas|ultimo|ultimos"
                    + "|precio|cotizacion|noticia|noticias|clima|pronostico)\\b.*");

    private final SemanticRouter delegate;
    private final ResearchEscalationDetector researchEscalationDetector;

    public GuardedSemanticRouter(SemanticRouter delegate) {
        this(delegate, new ResearchEscalationDetector());
    }

    public GuardedSemanticRouter(SemanticRouter delegate,
                                 ResearchEscalationDetector researchEscalationDetector) {
        this.delegate = Objects.requireNonNull(delegate, "delegate cannot be null");
        this.researchEscalationDetector = Objects.requireNonNull(researchEscalationDetector,
                "researchEscalationDetector cannot be null");
    }

    @Override
    public Capability classify(String command) {
        String normalized = normalize(command);
        if (isCurrentTimeRequest(normalized)) {
            return Capability.SYSTEM_TIME;
        }
        if (isAudioMutation(normalized)) {
            return Capability.AUDIO_CONTROL;
        }
        if (isLocalRequest(normalized)) {
            return Capability.OS_COMMAND;
        }
        if (isCurrentResearchRequest(normalized)) {
            return Capability.CURRENT_RESEARCH;
        }

        Capability modelDecision = Objects.requireNonNull(
                delegate.classify(command), "semantic router result cannot be null");
        return isCompatible(modelDecision, normalized) ? modelDecision : Capability.GENERAL;
    }

    private boolean isCompatible(Capability decision, String command) {
        return switch (decision) {
            case SYSTEM_TIME -> isCurrentTimeRequest(command);
            case AUDIO_CONTROL -> isAudioMutation(command);
            case OS_COMMAND -> isLocalRequest(command);
            case CURRENT_RESEARCH -> isCurrentResearchRequest(command);
            case GENERAL -> true;
        };
    }

    private boolean isCurrentTimeRequest(String command) {
        return CURRENT_TIME_REQUEST.matcher(command).matches();
    }

    private boolean isAudioMutation(String command) {
        if (!AUDIO_MUTATION.matcher(command).matches()) {
            return false;
        }
        return command.contains("volumen") || command.contains("voz")
                || command.contains("silencio") || command.contains("silencia")
                || command.contains("fuerte") || command.contains("bajo")
                || command.contains("hablar");
    }

    private boolean isLocalRequest(String command) {
        return APPLICATION_LIFECYCLE.matcher(command).matches()
                || LOCAL_QUERY.matcher(command).matches();
    }

    private boolean isCurrentResearchRequest(String command) {
        return researchEscalationDetector.shouldEscalate(command)
                || CURRENT_RESEARCH_REQUEST.matcher(command).matches()
                || EXPLICIT_RESEARCH_REQUEST.matcher(command).matches()
                || FRESH_INFORMATION_REQUEST.matcher(command).matches();
    }

    private String normalize(String command) {
        String value = Objects.requireNonNull(command, "command cannot be null").trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("command cannot be empty");
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("^[¡¿\\s]+|[!\\s]+$", "")
                .replaceAll("\\s+", " ");
    }
}

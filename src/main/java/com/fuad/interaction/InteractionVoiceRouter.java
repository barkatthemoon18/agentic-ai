package com.fuad.interaction;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class InteractionVoiceRouter {
    private static final Set<String> CANCEL_COMMANDS = Set.of(
            "cancelar", "cancela", "anular", "anula", "salir", "olvidalo");
    private static final Set<String> AFFIRMATIVE = Set.of(
            "si", "confirmar", "confirmo", "aceptar", "acepto");
    private static final Set<String> NEGATIVE = Set.of(
            "no", "rechazar", "rechazo");
    private static final List<Set<String>> ORDINALS = List.of(
            Set.of("1", "uno", "primero", "primera", "la primera", "el primero"),
            Set.of("2", "dos", "segundo", "segunda", "la segunda", "el segundo"),
            Set.of("3", "tres", "tercero", "tercera", "la tercera", "el tercero"),
            Set.of("4", "cuatro", "cuarto", "cuarta", "la cuarta", "el cuarto"),
            Set.of("5", "cinco", "quinto", "quinta", "la quinta", "el quinto"));

    private final DefaultInteractionService service;

    public InteractionVoiceRouter(DefaultInteractionService service) {
        this.service = java.util.Objects.requireNonNull(service);
    }

    public boolean hasActiveInteraction() {
        return service.activeSnapshot().isPresent();
    }

    public VoiceRouteOutcome route(String transcription) {
        ActiveInteractionSnapshot active = service.activeSnapshot().orElse(null);
        if (active == null) {
            return VoiceRouteOutcome.NO_ACTIVE_INTERACTION;
        }
        String normalized = normalize(transcription);
        if (CANCEL_COMMANDS.contains(normalized)) {
            return service.cancelVoice(active.sessionId())
                    ? VoiceRouteOutcome.CANCELLED : VoiceRouteOutcome.UNRESOLVABLE;
        }
        if (active.phase() != InteractionPhase.VISIBLE
                || !active.request().modalities().contains(InputModality.VOICE)) {
            return VoiceRouteOutcome.UNRESOLVABLE;
        }
        if (active.request() instanceof ChoiceRequest choice) {
            return resolveChoice(active.sessionId(), choice, normalized);
        }
        if (active.request() instanceof ConfirmationRequest) {
            if (AFFIRMATIVE.contains(normalized)) {
                return service.submitVoice(active.sessionId(), true)
                        ? VoiceRouteOutcome.RESOLVED : VoiceRouteOutcome.UNRESOLVABLE;
            }
            if (NEGATIVE.contains(normalized)) {
                return service.submitVoice(active.sessionId(), false)
                        ? VoiceRouteOutcome.RESOLVED : VoiceRouteOutcome.UNRESOLVABLE;
            }
        }
        return VoiceRouteOutcome.UNRESOLVABLE;
    }

    private VoiceRouteOutcome resolveChoice(java.util.UUID sessionId, ChoiceRequest request,
                                            String normalized) {
        Set<String> matchingIds = new HashSet<>();
        for (int index = 0; index < request.options().size(); index++) {
            ChoiceOption option = request.options().get(index);
            if (normalize(option.label()).equals(normalized)
                    || option.voiceAliases().stream().map(InteractionVoiceRouter::normalize)
                    .anyMatch(normalized::equals)
                    || (index < ORDINALS.size() && ORDINALS.get(index).contains(normalized))) {
                matchingIds.add(option.id());
            }
        }
        if (matchingIds.size() != 1) {
            return VoiceRouteOutcome.UNRESOLVABLE;
        }
        String selected = matchingIds.iterator().next();
        return service.submitVoice(sessionId, selected)
                ? VoiceRouteOutcome.RESOLVED : VoiceRouteOutcome.UNRESOLVABLE;
    }

    static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[¿?¡!.,;:]", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }
}

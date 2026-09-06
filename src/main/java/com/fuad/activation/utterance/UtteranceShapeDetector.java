package com.fuad.activation.utterance;

import com.fuad.assistant.session.ConversationSnapshot;
import com.fuad.enums.UtteranceDecision;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Resolves only utterance shapes whose classification does not require
 * probabilistic language interpretation. Ambiguous input is left to the LLM.
 */
public class UtteranceShapeDetector {
    private static final Pattern DATE_REQUEST = Pattern.compile(
            "^¿?(?:en )?que (?:fecha|dia) (?:es|estamos|cae)\\b.*\\??$");
    private static final Pattern EXPLICIT_PERSON_QUESTION = Pattern.compile(
            "^¿?quien (?:fue|es|era) \\S+(?: \\S+)*\\??$");
    private static final Pattern ASSISTANT_AUDIO_COMMAND = Pattern.compile(
            "^(?:pon|ajusta|cambia|sube|baja|silencia|activa|desactiva)\\b.*"
                    + "\\b(?:tu|la) (?:voz|volumen)\\b.*$");
    private static final Pattern SOURCE_FOLLOW_UP = Pattern.compile(
            "^¿?.*\\b(?:fuente|evidencia|referencia)\\b.*\\b(?:eso|esto|esa)\\b.*\\??$");
    private static final Pattern PERSONAL_FUTURE_REFLECTION = Pattern.compile(
            "^(?:algun dia|un dia)\\b.*\\b(?:aprendere|estudiare|entendere|dominare|sabre|podre)\\b.*$");
    private static final Pattern HUMAN_LIFECYCLE_ELLIPSIS = Pattern.compile(
            "^¿?y cuando (?:murio|nacio)\\??$");
    private static final Pattern CLEAR_CONTEXTUAL_REQUEST = Pattern.compile(
            "^(?:"
                    + "¿?y (?:por que|para que|hay|como|donde|cual)\\b.*"
                    + "|dame otro ejemplo\\b.*"
                    + "|¿?(?:puedes )?profundizar en (?:eso|esto)\\b.*"
                    + "|¿?que significa esa\\b.*"
                    + "|no entendi,? repitelo\\b.*"
                    + "|entonces resumelo\\b.*"
                    + ")$");
    private static final Pattern NAMED_PERSON = Pattern.compile(
            "\\b\\p{Lu}[\\p{Ll}áéíóúüñ]+\\s+\\p{Lu}[\\p{Ll}áéíóúüñ]+\\b");

    public Optional<UtteranceDecision> classify(UtteranceClassificationRequest request) {
        String text = normalize(request.getCurrentText());

        if (DATE_REQUEST.matcher(text).matches()
                || EXPLICIT_PERSON_QUESTION.matcher(text).matches()
                || ASSISTANT_AUDIO_COMMAND.matcher(text).matches()) {
            return Optional.of(UtteranceDecision.NEW_REQUEST);
        }

        if (PERSONAL_FUTURE_REFLECTION.matcher(text).matches()) {
            return Optional.of(UtteranceDecision.OTHER);
        }

        if (SOURCE_FOLLOW_UP.matcher(text).matches() && request.getPreviousTurn().isPresent()) {
            return Optional.of(UtteranceDecision.FOLLOW_UP);
        }

        if (HUMAN_LIFECYCLE_ELLIPSIS.matcher(text).matches()) {
            return Optional.of(hasNamedPersonAntecedent(request)
                    ? UtteranceDecision.FOLLOW_UP
                    : UtteranceDecision.OTHER);
        }

        if (CLEAR_CONTEXTUAL_REQUEST.matcher(text).matches()) {
            return Optional.of(request.getPreviousTurn().isPresent()
                    ? UtteranceDecision.FOLLOW_UP
                    : UtteranceDecision.OTHER);
        }

        return Optional.empty();
    }

    private boolean hasNamedPersonAntecedent(UtteranceClassificationRequest request) {
        return request.getPreviousTurn()
                .map(this::containsNamedPerson)
                .orElse(false);
    }

    private boolean containsNamedPerson(ConversationSnapshot snapshot) {
        return NAMED_PERSON.matcher(snapshot.getPreviousUserText()).find()
                || NAMED_PERSON.matcher(snapshot.getPreviousAssistantText()).find();
    }

    private String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }
}

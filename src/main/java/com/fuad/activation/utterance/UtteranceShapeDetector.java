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
    private static final Pattern REPORTED_SPEECH = Pattern.compile(
            "^(?:[a-zñ]+ ){1,3}(?:dijo|pregunto|comento|pidio|ordeno)\\b.*$");
    private static final Pattern FIRST_PERSON_TEMPORAL_EVENT = Pattern.compile(
            "^(?:ayer|anoche|manana|despues|luego)\\b.*(?:\\bvoy a\\b|"
                    + "\\b(?:abri|cerre|use|busque|pregunte|abrire|cerrare|usare)\\b).*$");
    private static final Pattern DIRECT_REQUEST = Pattern.compile(
            "^¿?(?:abre|ayudame|busca|cierra|dime|explicame|investiga|recomiendame|reinicia|"
                    + "resume|vuelve a)\\b.*$");
    private static final Pattern SOURCE_FOLLOW_UP = Pattern.compile(
            "^¿?.*\\b(?:fuente|evidencia|referencia)\\b.*\\b(?:eso|esto|esa)\\b.*\\??$");
    private static final Pattern PERSONAL_FUTURE_REFLECTION = Pattern.compile(
            "^(?:tal vez )?(?:algun dia|un dia)\\b.*\\b(?:aprendere|estudiare|entendere|"
                    + "dominare|sabre|podre|usare|use)\\b.*$");
    private static final Pattern HUMAN_LIFECYCLE_ELLIPSIS = Pattern.compile(
            "^¿?(?:y )?(?:cuando|donde) (?:murio|nacio)\\??$");
    private static final Pattern EXPLICIT_QUESTION = Pattern.compile(
            "^¿?(?:que|quien|cuando|donde|como|por que|para que|en que|cual|cuanto)\\b.*");
    private static final Pattern CLEAR_CONTEXTUAL_REQUEST = Pattern.compile(
            "^(?:"
                    + "¿?y (?:por que|para que|hay|como|donde|cual)\\b.*"
                    + "|dame otro ejemplo\\b.*"
                    + "|hazlo\\b.*"
                    + "|ponme (?:un|otro) ejemplo\\b.*"
                    + "|traducelo\\b.*"
                    + "|¿?cuanto (?:cuesta|vale)\\b.*"
                    + "|¿?(?:puedes )?profundizar en (?:eso|esto)\\b.*"
                    + "|¿?que significa esa\\b.*"
                    + "|no entendi,? repitelo\\b.*"
                    + "|entonces resumelo\\b.*"
                    + ")$");
    private static final Pattern NAMED_PERSON = Pattern.compile(
            "\\b\\p{Lu}[\\p{Ll}áéíóúüñ]+\\s+\\p{Lu}[\\p{Ll}áéíóúüñ]+\\b");

    public Optional<UtteranceDecision> classify(UtteranceClassificationRequest request) {
        String text = normalize(request.getCurrentText());

        // Attribution changes the addressee: an embedded command or question is
        // not a request to Ares, even when its inner clause looks imperative.
        if (REPORTED_SPEECH.matcher(text).matches()
                || FIRST_PERSON_TEMPORAL_EVENT.matcher(text).matches()) {
            return Optional.of(UtteranceDecision.OTHER);
        }

        // Compatibility rules must run before the generic contextual shape.
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

        if (DATE_REQUEST.matcher(text).matches()
                || EXPLICIT_PERSON_QUESTION.matcher(text).matches()
                || ASSISTANT_AUDIO_COMMAND.matcher(text).matches()
                || DIRECT_REQUEST.matcher(text).matches()
                || (EXPLICIT_QUESTION.matcher(text).matches()
                    && containsNamedPerson(request.getCurrentText()))) {
            return Optional.of(UtteranceDecision.NEW_REQUEST);
        }

        if (PERSONAL_FUTURE_REFLECTION.matcher(text).matches()) {
            return Optional.of(UtteranceDecision.OTHER);
        }

        if (SOURCE_FOLLOW_UP.matcher(text).matches() && request.getPreviousTurn().isPresent()) {
            return Optional.of(UtteranceDecision.FOLLOW_UP);
        }

        return Optional.empty();
    }

    private boolean hasNamedPersonAntecedent(UtteranceClassificationRequest request) {
        return request.getPreviousTurn()
                .map(this::containsNamedPerson)
                .orElse(false);
    }

    private boolean containsNamedPerson(ConversationSnapshot snapshot) {
        return containsNamedPerson(snapshot.getPreviousUserText())
                || containsNamedPerson(snapshot.getPreviousAssistantText());
    }

    private boolean containsNamedPerson(String text) {
        return NAMED_PERSON.matcher(text).find();
    }

    private String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }
}

package com.fuad.assistant.skills.general;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

public class DefaultGeneralBackendSelector implements GeneralBackendSelector {
    private static final String LOCAL_TARGET = "(?:qwen|modelo local)";
    private static final String GPT_TARGET = "(?:gpt|openai|modelo en la nube)";
    private static final String SWITCH_ACTION = "(?:usa|utiliza|emplea|usando|responde con|respondeme con|respondas con"
            + "|responder con|hazlo con|cambia a|cambialo a|pasa a|pasalo a"
            + "|vuelve a|prefiero|quiero usar|quiero que uses)";
    private static final Pattern LOCAL_PREFERENCE = Pattern.compile(".*\\b" + SWITCH_ACTION + "\\s+(?:" + LOCAL_TARGET + ")\\b.*");
    private static final Pattern GPT_PREFERENCE = Pattern.compile(
            ".*\\b" + SWITCH_ACTION + "\\s+(?:" + GPT_TARGET + ")\\b.*");
    private static final Pattern NEGATED_MODEL_SWITCH = Pattern.compile(
            ".*\\b(?:no|nunca)\\s+"
                    + "(?:(?:quiero\\s+que|quiero)\\s+)?"
                    + "(?:usar|utilizar|emplear|uses|respondas con|responder con"
                    + "|utilices|emplees|cambies a|cambiar a|pases a|pasar a)\\s+"
                    + "(?:" + LOCAL_TARGET + "|" + GPT_TARGET + ")\\b.*"
                    + "|.*\\bsin\\s+(?:usar|utilizar|emplear)\\s+"
                    + "(?:" + LOCAL_TARGET + "|" + GPT_TARGET + ")\\b.*");

    private final GeneralComplexityClassifier complexityClassifier;

    public DefaultGeneralBackendSelector(GeneralComplexityClassifier complexityClassifier) {
        this.complexityClassifier = Objects.requireNonNull(complexityClassifier,
                "complexityClassifier cannot be null");
    }

    @Override
    public GeneralBackendDecision select(String command, GeneralConversationState state) {
        String normalized = normalize(command);
        Optional<GeneralBackend> explicit = explicitBackend(normalized);
        if (explicit.isPresent()) {
            return new GeneralBackendDecision(explicit.get(), SelectionOrigin.EXPLICIT);
        }

        GeneralConversationState current = state == null ? GeneralConversationState.empty() : state;
        if (current.getActiveBackend().isPresent()) {
            return new GeneralBackendDecision(current.getActiveBackend().orElseThrow(),
                    current.getSelectionOrigin().orElseThrow());
        }

        try {
            return new GeneralBackendDecision(complexityClassifier.classify(command),
                    SelectionOrigin.AUTOMATIC);
        }
        catch (RuntimeException e) {
            System.err.println("General backend classification failed; defaulting to Qwen: " + e.getMessage());
            return new GeneralBackendDecision(GeneralBackend.QWEN_LOCAL, SelectionOrigin.AUTOMATIC);
        }
    }

    private Optional<GeneralBackend> explicitBackend(String command) {
        if (NEGATED_MODEL_SWITCH.matcher(command).matches()) {
            return Optional.empty();
        }
        boolean local = LOCAL_PREFERENCE.matcher(command).matches();
        boolean gpt = GPT_PREFERENCE.matcher(command).matches();

        if (local == gpt) {
            return Optional.empty();
        }
        return Optional.of(local ? GeneralBackend.QWEN_LOCAL : GeneralBackend.GPT);
    }

    private String normalize(String value) {
        String normalized = Objects.requireNonNull(value, "command cannot be null").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("command cannot be empty");
        }
        return Normalizer.normalize(normalized, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
    }
}

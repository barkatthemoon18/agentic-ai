package com.fuad.assistant.skills.general;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

public class DefaultGeneralBackendSelector implements GeneralBackendSelector {
    private static final Pattern PREFERENCE = Pattern.compile(
            ".*\\b(?:usa|utiliza|emplea|usando|responde con|respondeme con|respondas con"
                    + "|responder con|hazlo con|cambia a|cambialo a|pasa a|pasalo a|vuelve a"
                    + "|prefiero|quiero usar|quiero que uses)\\b.*");
    private static final Pattern LOCAL = Pattern.compile(".*\\b(?:qwen|modelo local)\\b.*");
    private static final Pattern GPT = Pattern.compile(".*\\b(?:gpt|openai|modelo en la nube)\\b.*");

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
        if (!PREFERENCE.matcher(command).matches()) {
            return Optional.empty();
        }
        boolean local = LOCAL.matcher(command).matches();
        boolean gpt = GPT.matcher(command).matches();
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

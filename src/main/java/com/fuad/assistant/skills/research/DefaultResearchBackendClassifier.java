package com.fuad.assistant.skills.research;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public class DefaultResearchBackendClassifier implements ResearchBackendClassifier {
    private static final Pattern LOCAL = Pattern.compile(
            ".*\\b(?:localmente|modelo local|con qwen|usando qwen)\\b.*");
    private static final Pattern GLOBAL = Pattern.compile(
            ".*\\b(?:globalmente|en internet|en la web|busqueda web|busqueda global)\\b.*");
    private static final Pattern CURRENT = Pattern.compile(
            ".*\\b(?:hoy|ahora|actual|actualmente|reciente|recientes|ultima|ultimas|ultimo|ultimos"
                    + "|precio|cotizacion|noticia|noticias|clima|pronostico|version estable|release)\\b.*");

    @Override
    public ResearchBackend classify(String query, ResearchBackend inheritedBackend) {
        String normalized = normalize(query);
        if (LOCAL.matcher(normalized).matches()) {
            return ResearchBackend.QWEN_LOCAL;
        }
        if (GLOBAL.matcher(normalized).matches()) {
            return ResearchBackend.GPT_WEB;
        }
        if (CURRENT.matcher(normalized).matches()) {
            return ResearchBackend.GPT_WEB;
        }
        if (inheritedBackend != null) {
            return inheritedBackend;
        }
        return ResearchBackend.QWEN_LOCAL;
    }

    private String normalize(String value) {
        String normalized = Objects.requireNonNull(value, "query cannot be null").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("query cannot be empty");
        }
        return Normalizer.normalize(normalized, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
    }
}

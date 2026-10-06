package com.fuad.assistant.routing;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public class ResearchEscalationDetector {
    private static final Pattern NEGATED_WEB_SIGNAL = Pattern.compile(
            "\\b(?:sin\\s+(?:(?:buscar|consultar|usar|utilizar|navegar|salir)\\s+"
                    + "(?:(?:en|a)\\s+)?)?(?:internet|(?:la\\s+)?web|online)"
                    + "|(?:no|nunca)\\s+(?:lo\\s+)?(?:busques?|consultes?|uses?|utilices?|navegues?)"
                    + "\\s+(?:(?:en|a)\\s+)?(?:internet|(?:la\\s+)?web|online))\\b");
    private static final Pattern NEGATED_RESEARCH = Pattern.compile(
            "^(?:(?:por favor|porfa),?\\s+)?(?:no|nunca)\\s+(?:lo\\s+)?"
                    + "(?:busques?|investigues?|consultes?|verifiques?|compruebes?)\\b.*"
                    + "|^sin\\s+(?:buscar|investigar|consultar|verificar|comprobar)\\b.*"
                    + "|^(?:(?:por favor|porfa),?\\s+)?(?:no|nunca)\\s+"
                    + "(?:uses?|utilices?)\\s+(?:internet|la web|web|online)\\b.*");
    private static final Pattern EXPLICIT_RESEARCH = Pattern.compile(
            "^(?:ahora\\s+)?(?:busca|buscar|buscame|buscalo|buscala|investiga|investigar"
                    + "|investigalo|investigala)\\b.*");
    private static final Pattern WEB_REQUEST = Pattern.compile(
            ".*\\b(?:busca|buscar|buscame|buscalo|buscala|consulta|consultar"
                    + "|verifica|verificar|comprueba|comprobar)\\b"
                    + ".*\\b(?:internet|web|online)\\b.*"
                    + "|.*\\b(?:internet|web|online)\\b.*\\b(?:busca|buscar|buscame|buscalo|buscala"
                    + "|consulta|consultar"
                    + "|verifica|verificar|comprueba|comprobar)\\b.*");
    private static final Pattern SOURCES = Pattern.compile(
            ".*\\b(?:dame|dime|muestra|cita|incluye|consulta|busca|encuentra)\\b.*"
                    + "\\b(?:fuente|fuentes|referencia|referencias)\\b.*"
                    + "|.*\\b(?:cual|cuales)\\b.*\\b(?:tus|las)\\s+"
                    + "(?:fuente|fuentes|referencia|referencias)\\b.*"
                    + "|.*\\bde donde\\b.*\\b(?:sale|sacaste|obtuviste)\\b.*");
    private static final Pattern VERIFY_CURRENT = Pattern.compile(
            ".*\\b(?:verifica|comprueba|confirma)\\b.*\\b(?:sigue|aun|todavia|actual|vigente|cierto)\\b.*");
    private static final Pattern TODAY = Pattern.compile(
            ".*\\b(?:que paso|que ocurrio|que ha pasado)\\b.*\\bhoy\\b.*");
    private static final Pattern CURRENT_VALUE = Pattern.compile(
            ".*\\b(?:precio|cotizacion|valor|version)\\b.*\\b(?:actual|ultima|ultimo)\\b.*"
                    + "|.*\\b(?:actual|ultima|ultimo)\\b.*\\b(?:precio|cotizacion|valor|version)\\b.*");
    private static final Pattern LATEST_NEWS = Pattern.compile(
            ".*\\b(?:ultima|ultimas|ultimo|ultimos|reciente|recientes)\\b.*"
                    + "\\b(?:noticia|noticias|release|releases)\\b.*"
                    + "|.*\\b(?:noticia|noticias|release|releases)\\b.*"
                    + "\\b(?:ultima|ultimas|ultimo|ultimos|reciente|recientes)\\b.*");

    public boolean isExplicitlyNegated(String command) {
        return NEGATED_RESEARCH.matcher(normalize(command)).matches();
    }

    public boolean shouldEscalate(String command) {
        String normalized = withoutNegatedWebSignals(command);
        if (normalized.isBlank() || NEGATED_RESEARCH.matcher(normalized).matches()) {
            return false;
        }
        return EXPLICIT_RESEARCH.matcher(normalized).matches()
                || WEB_REQUEST.matcher(normalized).matches()
                || SOURCES.matcher(normalized).matches()
                || VERIFY_CURRENT.matcher(normalized).matches()
                || TODAY.matcher(normalized).matches()
                || CURRENT_VALUE.matcher(normalized).matches()
                || LATEST_NEWS.matcher(normalized).matches();
    }

    // This copy is only for deterministic signals; callers keep the original model input.
    String withoutNegatedWebSignals(String command) {
        return NEGATED_WEB_SIGNAL.matcher(normalize(command)).replaceAll(" ")
                .replaceAll("^[\\s,;]+|[\\s,;]+$", "")
                .replaceAll("\\s+", " ");
    }

    private String normalize(String command) {
        String value = Objects.requireNonNull(command, "command cannot be null").trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("command cannot be empty");
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("^[¡¿\\s]+|[!?.\\s]+$", "")
                .replaceAll("\\s+", " ");
    }
}

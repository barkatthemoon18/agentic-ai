package com.fuad.assistant.skills.os;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public class OsCommandSafetyGuard {
    private static final Pattern NON_IMMEDIATE_PATTERN = Pattern.compile("^(manana|ayer|despues|mas tarde|luego)\\b");
    private static final Pattern DESCRIPTIVE_PATTERN = Pattern.compile("\\b(se esta cerrando|se cerro solo|esta cerrado)\\b");
    private static final Pattern FUTURE_ACTION_PATTERN = Pattern.compile("\\b(voy a|vamos a|pienso|creo que voy a)\\s+(abrir|cerrar)\\b");

    public boolean canExecute(String command) {
        String normalized = normalize(command);
        if (NON_IMMEDIATE_PATTERN.matcher(normalized).find()) {
            return false;
        }
        if (DESCRIPTIVE_PATTERN.matcher(normalized).find()) {
            return false;
        }
        if (FUTURE_ACTION_PATTERN.matcher(normalized).find()) {
            return false;
        }
        return true;
    }

    private String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }
}

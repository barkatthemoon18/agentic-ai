package com.fuad.assistant.skills.os;

import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public record ApplicationWindowSignature(String className, String titlePattern) {
    public ApplicationWindowSignature {
        className = className == null ? "" : className.trim();
        titlePattern = titlePattern == null ? "" : titlePattern.trim();
        if (className.isBlank() && titlePattern.isBlank()) {
            throw new IllegalArgumentException("A window signature needs a class name or title pattern");
        }
        if (!titlePattern.isBlank()) {
            try {
                Pattern.compile(titlePattern);
            }
            catch (PatternSyntaxException error) {
                throw new IllegalArgumentException("Invalid window title pattern: " + titlePattern, error);
            }
        }
    }

    boolean matches(WindowService.WindowHandle window) {
        Objects.requireNonNull(window, "window cannot be null");
        if (!className.isBlank() && !className.equalsIgnoreCase(window.className())) return false;
        return titlePattern.isBlank() || Pattern.compile(titlePattern).matcher(window.title()).matches();
    }
}

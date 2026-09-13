package com.fuad.assistant.skills.os;

import java.util.ArrayList;
import java.util.List;

/** Tokenizes a Windows command line using the CommandLineToArgvW backslash and quote rules. */
final class WindowsCommandLineTokenizer {
    private WindowsCommandLineTokenizer() { }

    static List<String> tokenize(String commandLine) {
        if (commandLine == null || commandLine.isBlank()) return List.of();
        List<String> arguments = new ArrayList<>();
        int index = 0;
        while (index < commandLine.length()) {
            while (index < commandLine.length() && Character.isWhitespace(commandLine.charAt(index))) index++;
            if (index == commandLine.length()) break;
            StringBuilder argument = new StringBuilder();
            boolean quoted = false;
            while (index < commandLine.length()) {
                char current = commandLine.charAt(index);
                if (!quoted && Character.isWhitespace(current)) break;
                int backslashes = 0;
                while (index < commandLine.length() && commandLine.charAt(index) == '\\') {
                    backslashes++;
                    index++;
                }
                if (index < commandLine.length() && commandLine.charAt(index) == '"') {
                    argument.append("\\".repeat(backslashes / 2));
                    if (backslashes % 2 == 0) quoted = !quoted;
                    else argument.append('"');
                    index++;
                    continue;
                }
                argument.append("\\".repeat(backslashes));
                if (index >= commandLine.length()) break;
                argument.append(commandLine.charAt(index++));
            }
            arguments.add(argument.toString());
            while (index < commandLine.length() && Character.isWhitespace(commandLine.charAt(index))) index++;
        }
        return List.copyOf(arguments);
    }
}

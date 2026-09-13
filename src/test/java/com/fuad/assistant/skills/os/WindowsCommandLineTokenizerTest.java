package com.fuad.assistant.skills.os;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WindowsCommandLineTokenizerTest {
    @Test
    void shouldPreserveQuotedArgumentsAndEscapedQuotes() {
        assertEquals(List.of("firefox.exe", "--app-id=prime", "C:\\My Project", "say \"hello\""),
                WindowsCommandLineTokenizer.tokenize(
                        "firefox.exe --app-id=prime \"C:\\My Project\" \"say \\\"hello\\\"\""));
    }

    @Test
    void shouldReturnEmptyListForBlankInput() {
        assertEquals(List.of(), WindowsCommandLineTokenizer.tokenize("  "));
    }
}

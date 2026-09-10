package com.fuad.assistant.skills.general;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LocalGeneralComplexityClassifierTest {
    @Test
    void shouldParseClosedLabels() {
        assertEquals(GeneralBackend.QWEN_LOCAL, LocalGeneralComplexityClassifier.parse("local"));
        assertEquals(GeneralBackend.GPT, LocalGeneralComplexityClassifier.parse("```text\ngpt\n```"));
    }

    @Test
    void shouldRejectUnknownLabels() {
        assertThrows(IllegalStateException.class,
                () -> LocalGeneralComplexityClassifier.parse("research"));
    }
}

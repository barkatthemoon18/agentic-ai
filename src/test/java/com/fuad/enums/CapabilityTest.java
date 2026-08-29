package com.fuad.enums;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CapabilityTest {
    @ParameterizedTest
    @CsvSource({
            "system-time, SYSTEM_TIME",
            "' SYSTEM-TIME ', SYSTEM_TIME",
            "audio-control, AUDIO_CONTROL",
            "os-command, OS_COMMAND",
            "current-research, CURRENT_RESEARCH",
            "general, GENERAL"
    })
    void shouldParseExternalValue(String value, Capability expected) {
        assertEquals(expected, Capability.fromValue(value));
    }

    @Test
    void shouldRejectUnknownValue() {
        assertThrows(IllegalArgumentException.class, () -> Capability.fromValue("weather"));
    }
}

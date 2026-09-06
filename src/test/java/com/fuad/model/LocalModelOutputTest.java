package com.fuad.model;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LocalModelOutputTest {

    @Test
    void shouldExtractAllowedLabelAtTheBeginningOnly() {
        Set<String> labels = Set.of("new_request", "follow_up", "other");

        assertEquals("new_request", LocalModelOutput.extractLeadingLabel(
                "new_request\n\nThe current utterance is independent.", labels, "test"));
        assertEquals("follow_up", LocalModelOutput.extractLeadingLabel(
                "\"follow_up\"", labels, "test"));
        assertEquals("other", LocalModelOutput.extractLeadingLabel(
                "**other**: explanation", labels, "test"));
    }

    @Test
    void shouldRejectUnknownOrEmbeddedLabels() {
        Set<String> labels = Set.of("new_request", "follow_up", "other");

        assertThrows(IllegalStateException.class, () -> LocalModelOutput.extractLeadingLabel(
                "recommendation", labels, "test"));
        assertThrows(IllegalStateException.class, () -> LocalModelOutput.extractLeadingLabel(
                "The answer is new_request", labels, "test"));
    }

    @Test
    void shouldExtractFirstStructuredContractLine() {
        assertEquals("mute|assistant|none", LocalModelOutput.firstContractLine(
                "\"mute|assistant|none\"\nExplanation"));
    }
}

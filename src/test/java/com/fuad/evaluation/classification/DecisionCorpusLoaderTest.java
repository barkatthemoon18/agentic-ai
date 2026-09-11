package com.fuad.evaluation.classification;

import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecisionCorpusLoaderTest {
    private final DecisionCorpusLoader loader = new DecisionCorpusLoader();

    @Test
    void shouldLoadCasesWithOptionalInheritedBackend() {
        String corpus = """
                {"id":"fresh-01","query":"¿Qué precio tiene?","expected":"gpt_web","tags":["current"],"rationale":"Dato actual"}
                {"id":"follow-01","query":"Explícalo mejor","expected":"qwen_local","tags":["follow-up"],"rationale":"Hereda la rama","inheritedBackend":"qwen_local"}
                """;

        List<DecisionCorpusCase> cases = loader.load(new StringReader(corpus), "inline.jsonl",
                Set.of("qwen_local", "gpt_web"), Set.of("qwen_local", "gpt_web"));

        assertEquals(2, cases.size());
        assertNull(cases.getFirst().normalizedInheritedBackend());
        assertEquals("qwen_local", cases.getLast().normalizedInheritedBackend());
    }

    @Test
    void shouldReportMalformedJsonAndDuplicateIdentifiers() {
        IllegalArgumentException malformed = assertThrows(IllegalArgumentException.class,
                () -> loader.load(new StringReader("{bad}"), "broken.jsonl",
                        Set.of("quick", "deep"), Set.of()));
        assertTrue(malformed.getMessage().contains("broken.jsonl:1"));

        String duplicate = """
                {"id":"same","query":"uno","expected":"quick","tags":[],"rationale":"dato"}
                {"id":"same","query":"dos","expected":"deep","tags":[],"rationale":"análisis"}
                """;
        IllegalArgumentException repeated = assertThrows(IllegalArgumentException.class,
                () -> loader.load(new StringReader(duplicate), "duplicate.jsonl",
                        Set.of("quick", "deep"), Set.of()));
        assertTrue(repeated.getMessage().contains("duplicate.jsonl:2"));
    }

    @Test
    void shouldRejectUnknownExpectedAndInheritedLabels() {
        String unknownExpected = """
                {"id":"bad","query":"consulta","expected":"medium","tags":[],"rationale":"inválida"}
                """;
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> loader.load(new StringReader(unknownExpected), "labels.jsonl",
                        Set.of("quick", "deep"), Set.of())).getMessage().contains("unknown expected label"));

        String unknownInherited = """
                {"id":"bad","query":"continúa","expected":"quick","tags":[],"rationale":"inválida","inheritedBackend":"other"}
                """;
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> loader.load(new StringReader(unknownInherited), "inherited.jsonl",
                        Set.of("quick", "deep"), Set.of())).getMessage().contains("unknown inherited backend"));
    }

    @Test
    void shouldRequireMetadataAndNonEmptyCorpus() {
        String missingTags = """
                {"id":"bad","query":"consulta","expected":"quick","rationale":"sin tags"}
                """;
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> loader.load(new StringReader(missingTags), "metadata.jsonl",
                        Set.of("quick", "deep"), Set.of())).getMessage().contains("tags cannot be null"));
        assertEquals("Corpus contains no cases: empty.jsonl", assertThrows(IllegalArgumentException.class,
                () -> loader.load(new StringReader("\n"), "empty.jsonl",
                        Set.of("quick", "deep"), Set.of())).getMessage());
    }
}

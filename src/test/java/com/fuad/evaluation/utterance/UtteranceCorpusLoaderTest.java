package com.fuad.evaluation.utterance;

import com.fuad.enums.Capability;
import com.fuad.enums.UtteranceDecision;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UtteranceCorpusLoaderTest {
    private final UtteranceCorpusLoader loader = new UtteranceCorpusLoader();

    @Test
    void shouldLoadContextFreeAndContextualCases() {
        String corpus = """
                {"id":"new-001","currentText":"¿Qué hora es?","contextAvailable":false,"expected":"new_request","tags":["direct"],"rationale":"Petición independiente"}
                {"id":"follow-001","currentText":"¿Y para qué se usa?","contextAvailable":true,"previousUserText":"Explícame RSA","previousAssistantText":"RSA es criptografía asimétrica.","owner":"general","expected":"follow_up","tags":["critical","pronoun"],"rationale":"Depende del tema RSA"}
                """;

        List<UtteranceEvaluationCase> cases = loader.load(new StringReader(corpus), "inline.jsonl");

        assertEquals(2, cases.size());
        assertEquals(UtteranceDecision.NEW_REQUEST, cases.get(0).expectedDecision());
        assertFalse(cases.get(0).toClassificationRequest().getPreviousTurn().isPresent());
        assertEquals(UtteranceDecision.FOLLOW_UP, cases.get(1).expectedDecision());
        assertEquals(Capability.GENERAL, cases.get(1).toClassificationRequest()
                .getPreviousTurn().orElseThrow().getOwner());
        assertTrue(cases.get(1).hasTag("CRITICAL"));
    }

    @Test
    void shouldReportMalformedJsonWithSourceAndLine() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> loader.load(new StringReader("{not-json}"), "broken.jsonl"));

        assertTrue(exception.getMessage().contains("broken.jsonl:1"));
        assertTrue(exception.getMessage().contains("invalid JSON"));
    }

    @Test
    void shouldRejectDuplicateIdentifiers() {
        String corpus = """
                {"id":"same","currentText":"hola","contextAvailable":false,"expected":"other","tags":[],"rationale":"Ambiental"}
                {"id":"same","currentText":"adiós","contextAvailable":false,"expected":"other","tags":[],"rationale":"Ambiental"}
                """;

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> loader.load(new StringReader(corpus), "duplicate.jsonl"));

        assertTrue(exception.getMessage().contains("duplicate.jsonl:2"));
        assertTrue(exception.getMessage().contains("duplicate id 'same'"));
    }

    @Test
    void shouldRejectFollowUpWithoutContext() {
        String corpus = """
                {"id":"invalid","currentText":"¿Y por qué?","contextAvailable":false,"expected":"follow_up","tags":[],"rationale":"Caso inválido"}
                """;

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> loader.load(new StringReader(corpus), "invalid.jsonl"));

        assertTrue(exception.getMessage().contains("FOLLOW_UP requires contextAvailable=true"));
    }

    @Test
    void shouldRejectContextFieldsWhenContextIsUnavailable() {
        String corpus = """
                {"id":"invalid","currentText":"hola","contextAvailable":false,"previousUserText":"texto previo","expected":"other","tags":[],"rationale":"Caso inválido"}
                """;

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> loader.load(new StringReader(corpus), "invalid.jsonl"));

        assertTrue(exception.getMessage().contains("context fields require contextAvailable=true"));
    }

    @Test
    void shouldRejectAnEmptyCorpus() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> loader.load(new StringReader("\n  \n"), "empty.jsonl"));

        assertEquals("Corpus contains no cases: empty.jsonl", exception.getMessage());
    }
}

package com.fuad.evaluation.utterance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuad.enums.UtteranceDecision;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class UtteranceComparisonTest {
    @TempDir Path directory;

    @Test
    void shouldSelectModesAndRejectUnknownMode() {
        assertEquals(List.of(UtteranceComparison.Mode.HYBRID), UtteranceComparison.Mode.parse("hybrid"));
        assertEquals(List.of(UtteranceComparison.Mode.MODEL_ONLY), UtteranceComparison.Mode.parse("model-only"));
        assertEquals(2, UtteranceComparison.Mode.parse("both").size());
        assertThrows(IllegalArgumentException.class, () -> UtteranceComparison.Mode.parse("unknown"));
    }

    @Test
    void shouldRepeatAndMeasureDecisionChangesIncludingErrors() {
        var cases = new UtteranceCorpusLoader().loadResource("evaluation/utterance-development.jsonl").subList(0, 2);
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger writes = new AtomicInteger();
        var reports = UtteranceComparison.repeat(request -> {
            int call = calls.getAndIncrement();
            if (call == 2) throw new IllegalStateException("simulated");
            return UtteranceDecision.NEW_REQUEST;
        }, cases, 3, (iteration, report) -> assertEquals(writes.incrementAndGet(), iteration));
        assertEquals(6, calls.get());
        assertEquals(3, writes.get());
        assertEquals(0.5, UtteranceComparison.instability(reports));
        assertEquals(0.0, UtteranceComparison.instability(List.of(reports.getFirst())));
        assertThrows(IllegalArgumentException.class, () -> UtteranceComparison.repeat(
                request -> UtteranceDecision.OTHER, cases, 0, (i, report) -> fail()));
    }

    @Test
    void shouldPersistMetadataEveryOutcomeAndSeparateRuleLatencyWithoutOverwriting() throws Exception {
        var item = new UtteranceEvaluationCase();
        item.setId("rules");
        item.setCurrentText("Explícame RSA");
        item.setContextAvailable(false);
        item.setExpected("new_request");
        item.setTags(List.of("critical"));
        item.setRationale("test");
        var report = new UtteranceCorpusEvaluator().evaluate(request -> UtteranceDecision.NEW_REQUEST, List.of(item));
        var metadata = Map.<String, Object>of("model", "test-model", "promptSha256", "abc", "corpusSha256", "def");
        Path path = directory.resolve("run.json");
        UtteranceComparison.write(path, UtteranceComparison.artifact(UtteranceComparison.Mode.HYBRID, 2, report, metadata));
        var json = new ObjectMapper().readTree(Files.readString(path));
        assertEquals("abc", json.get("promptSha256").asText());
        assertEquals(2, json.get("repetition").asInt());
        assertEquals("rules", json.get("results").get(0).get("route").asText());
        assertEquals(1, json.get("byRoute").get("rules").get("cases").asInt());
        assertEquals(0, json.get("metrics").get("errors").asInt());
        assertThrows(IllegalStateException.class, () -> UtteranceComparison.write(path, Map.of()));
        var modelOnly = UtteranceComparison.artifact(UtteranceComparison.Mode.MODEL_ONLY, 1, report, metadata);
        assertTrue(new ObjectMapper().writeValueAsString(modelOnly).contains("\"route\":\"model\""));
    }
}

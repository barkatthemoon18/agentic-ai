package com.fuad.evaluation.stability;

import com.fuad.evaluation.classification.DecisionCorpusLoader;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class StabilityMetricsTest {
    @Test
    void fiveIdenticalWrongDecisionsMustBeADeterministicRegression() {
        var samples = java.util.stream.IntStream.range(0, 5).mapToObj(i -> row("qwen_local", "gpt", null, 1, true, "model")).toList();
        var result = StabilityMetrics.stability(samples, 5).getFirst();
        assertEquals("deterministic-regression", result.get("classification"));
        assertEquals(true, result.get("stable"));
        assertEquals(false, result.get("strictPass"));
        assertEquals(0L, result.get("correctRuns"));
    }

    @Test
    void variationAndErrorsMustRemainDistinctFromFiveCorrectResults() {
        List<Map<String, Object>> samples = new ArrayList<>();
        for (int i = 0; i < 4; i++) samples.add(row("local", "local", null, 1, true, "model"));
        samples.add(row("local", null, "invalid output", 2, false, "error"));
        assertEquals("unstable", StabilityMetrics.stability(samples, 5).getFirst().get("classification"));
        assertFalse((Boolean) StabilityMetrics.stability(samples, 5).getFirst().get("strictPass"));
        samples.set(4, row("local", "local", null, 2, true, "model"));
        assertTrue((Boolean) StabilityMetrics.stability(samples, 5).getFirst().get("strictPass"));
        assertFalse((Boolean) StabilityMetrics.stability(samples.subList(0, 4), 5).getFirst().get("strictPass"));
    }

    @Test
    void recoveredRetriesFallbackAndRuleOnlyCallsMustNotBeAttributedToFirstPassModelSuccess() {
        var metrics = StabilityMetrics.metrics(List.of(
                row("local", "local", null, 2, true, "model"),
                row("local", "local", null, 2, false, "fallback"),
                row("gpt", "gpt", null, 0, false, "rules"),
                row("gpt", null, "invalid retry", 2, false, "error")), List.of("local", "gpt"));
        assertEquals(1L, metrics.get("errors"));
        assertEquals(0L, metrics.get("firstPassValid"));
        assertEquals(3L, metrics.get("retryCount"));
        assertEquals(1L, metrics.get("retryRecovered"));
        assertEquals(.75, metrics.get("accuracy"));
    }

    @Test
    void diagnosticCorpusMustHaveTwelveCasesPerLabelAndMatchingVerbSupport() {
        var cases = new DecisionCorpusLoader().loadResource("evaluation/general-backend-contract-regression.jsonl",
                Set.of("qwen_local", "gpt"), Set.of());
        assertEquals(24, cases.size());
        for (String label : List.of("qwen_local", "gpt")) {
            assertEquals(12, cases.stream().filter(c -> c.expected().equals(label)).count());
            for (String verb : List.of("profundiza", "detalla", "amplía", "desarrolla", "explica en profundidad", "analiza a fondo"))
                assertEquals(2, cases.stream().filter(c -> c.expected().equals(label) && c.tags().contains("verb:" + verb))
                        .peek(c -> assertTrue(c.query().toLowerCase().startsWith(verb))).count());
        }
    }

    private Map<String, Object> row(String expected, String actual, String error, int attempts, boolean rawValid, String route) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", "case"); row.put("query", "query"); row.put("expected", expected);
        row.put("actual", actual); row.put("error", error); row.put("attempts", attempts);
        row.put("lastModelOutputValid", rawValid); row.put("route", route); row.put("latencyMillis", 1.0);
        return row;
    }

    @Test
    void planProjectionsMustKeepAccessAndDepthErrorsIndependent() {
        var values = List.of(
                row("knowledge_quick", "web_quick", null, 1, true, "model"),
                row("knowledge_deep", "knowledge_quick", null, 1, true, "model"),
                row("web_quick", "web_quick", null, 1, true, "model"),
                row("web_deep", null, "invalid", 2, false, "error"));
        var access = StabilityMetrics.project(values, List.of("knowledge", "web"), true);
        var depth = StabilityMetrics.project(values, List.of("quick", "deep"), false);
        assertEquals(.5, access.get("accuracy"));
        assertEquals(.5, depth.get("accuracy"));
        assertEquals(1L, access.get("errors"));
        assertEquals(1L, depth.get("errors"));
        assertEquals(1L, ((Map<?, ?>) ((Map<?, ?>) access.get("confusionMatrix")).get("web")).get("ERROR"));
    }

    @Test
    void invalidOutputRateMustCountCompletedResponsesAndExcludeProviderFailures() {
        var recovered = row("web_quick", "web_quick", null, 2, true, "model");
        recovered.put("inference", List.of(
                Map.of("outputValid", false), Map.of("outputValid", true)));
        var provider = row("web_quick", null, "provider offline", 1, false, "error");
        provider.put("inference", List.of(Map.of("error", "provider offline", "outputValid", false)));
        var metric = StabilityMetrics.metrics(List.of(recovered, provider), List.of("web_quick"));
        assertEquals(1L, metric.get("invalidOutputs"));
        assertEquals(.5, metric.get("invalidOutputRate"));
        assertEquals(0L, metric.get("finalInvalidOutputs"));
    }
}

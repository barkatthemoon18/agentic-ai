package com.fuad.evaluation.stability;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

final class StabilityMetrics {
    private StabilityMetrics() {}

    static Map<String, Object> metrics(List<Map<String, Object>> rows, List<String> labels) {
        Map<String, Object> result = new LinkedHashMap<>();
        long correct = rows.stream().filter(StabilityMetrics::correct).count();
        long errors = rows.stream().filter(row -> row.get("error") != null || row.get("actual") == null).count();
        Map<String, Object> perLabel = new LinkedHashMap<>();
        Map<String, Object> confusion = new LinkedHashMap<>();
        double macroF1 = 0;
        for (String label : labels) {
            long support = rows.stream().filter(row -> label.equals(row.get("expected"))).count();
            long predicted = rows.stream().filter(row -> label.equals(row.get("actual"))).count();
            long tp = rows.stream().filter(StabilityMetrics::correct)
                    .filter(row -> label.equals(row.get("expected"))).count();
            double precision = ratio(tp, predicted), recall = ratio(tp, support);
            double f1 = precision + recall == 0 ? 0 : 2 * precision * recall / (precision + recall);
            macroF1 += f1;
            perLabel.put(label, Map.of("support", support, "precision", precision, "recall", recall, "f1", f1));
            Map<String, Long> counts = new LinkedHashMap<>();
            for (String actual : labels) counts.put(actual, rows.stream()
                    .filter(row -> label.equals(row.get("expected")) && actual.equals(row.get("actual"))).count());
            counts.put("ERROR", rows.stream().filter(row -> label.equals(row.get("expected")))
                    .filter(row -> row.get("actual") == null || row.get("error") != null).count());
            confusion.put(label, counts);
        }
        List<Double> latencies = rows.stream().map(row -> ((Number) row.get("latencyMillis")).doubleValue())
                .sorted().toList();
        result.put("cases", rows.size());
        result.put("correct", correct);
        result.put("errors", errors);
        result.put("accuracy", ratio(correct, rows.size()));
        result.put("macroF1", macroF1 / labels.size());
        result.put("byLabel", perLabel);
        result.put("confusionMatrix", confusion);
        result.put("p50Millis", percentile(latencies, .5));
        result.put("p95Millis", percentile(latencies, .95));
        result.put("inferenceCalls", rows.stream().mapToInt(row -> ((Number) row.get("attempts")).intValue()).sum());
        result.put("firstPassValid", rows.stream().filter(row -> ((Number) row.get("attempts")).intValue() == 1)
                .filter(row -> Boolean.TRUE.equals(row.get("lastModelOutputValid"))).count());
        long retries = rows.stream().filter(row -> ((Number) row.get("attempts")).intValue() > 1).count();
        result.put("retryCount", retries);
        result.put("retryRate", ratio(retries, rows.size()));
        result.put("retryRecovered", rows.stream().filter(row -> ((Number) row.get("attempts")).intValue() > 1)
                .filter(row -> Boolean.TRUE.equals(row.get("lastModelOutputValid"))).count());
        result.put("routes", rows.stream().collect(Collectors.groupingBy(row -> row.get("route").toString(),
                LinkedHashMap::new, Collectors.counting())));
        List<Map<String, Object>> completed = rows.stream()
                .flatMap(row -> ((List<Map<String, Object>>) row.getOrDefault("inference", List.of())).stream())
                .filter(attempt -> attempt.get("error") == null).toList();
        long invalid = completed.stream().filter(attempt -> Boolean.FALSE.equals(attempt.get("outputValid"))).count();
        result.put("invalidOutputs", invalid);
        result.put("invalidOutputRate", ratio(invalid, completed.size()));
        result.put("finalInvalidOutputs", rows.stream().filter(row -> ((Number) row.get("attempts")).intValue() > 0)
                .filter(row -> row.get("error") != null && !Boolean.TRUE.equals(row.get("lastModelOutputValid")))
                .filter(row -> ((List<Map<String, Object>>) row.getOrDefault("inference", List.of())).stream()
                        .noneMatch(attempt -> attempt.get("error") != null)).count());
        return result;
    }

    static Map<String, Object> project(List<Map<String, Object>> rows, List<String> labels, boolean access) {
        List<Map<String, Object>> projected = rows.stream().map(row -> {
            Map<String, Object> copy = new LinkedHashMap<>(row);
            copy.put("expected", component(row.get("expected"), access));
            copy.put("actual", component(row.get("actual"), access));
            return copy;
        }).toList();
        return metrics(projected, labels);
    }

    private static String component(Object label, boolean access) {
        if (label == null) return null;
        String[] parts = label.toString().split("_");
        return parts[access ? 0 : 1];
    }

    static List<Map<String, Object>> stability(List<Map<String, Object>> rows, int repetitions) {
        Map<String, List<Map<String, Object>>> byId = rows.stream().collect(Collectors.groupingBy(
                row -> row.get("id").toString(), LinkedHashMap::new, Collectors.toList()));
        return byId.entrySet().stream().map(entry -> {
            List<Map<String, Object>> samples = entry.getValue();
            List<String> outcomes = samples.stream().map(row -> row.get("error") != null || row.get("actual") == null
                    ? "ERROR" : row.get("actual").toString()).toList();
            long distinct = outcomes.stream().distinct().count();
            long correct = samples.stream().filter(StabilityMetrics::correct).count();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", entry.getKey());
            result.put("query", samples.getFirst().get("query"));
            result.put("expected", samples.getFirst().get("expected"));
            result.put("outcomes", outcomes);
            result.put("correctRuns", correct);
            result.put("complete", samples.size() == repetitions);
            result.put("stable", samples.size() == repetitions && distinct == 1 && !outcomes.contains("ERROR"));
            result.put("strictPass", samples.size() == repetitions && correct == repetitions && distinct == 1);
            result.put("classification", samples.size() != repetitions ? "incomplete"
                    : distinct > 1 ? "unstable" : outcomes.contains("ERROR") ? "deterministic-error"
                    : correct != repetitions ? "deterministic-regression" : "correct-stable");
            return result;
        }).toList();
    }

    static boolean correct(Map<String, Object> row) {
        return row.get("error") == null && row.get("expected").equals(row.get("actual"));
    }
    private static double ratio(long count, long total) { return total == 0 ? 0 : (double) count / total; }
    private static double percentile(List<Double> values, double quantile) {
        return values.isEmpty() ? 0 : values.get(Math.max(0, (int) Math.ceil(values.size() * quantile) - 1));
    }
}

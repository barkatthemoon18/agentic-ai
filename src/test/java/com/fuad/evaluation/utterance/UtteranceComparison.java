package com.fuad.evaluation.utterance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuad.activation.utterance.UtteranceClassifier;
import com.fuad.activation.utterance.UtteranceShapeDetector;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

final class UtteranceComparison {
    enum Mode {
        HYBRID, MODEL_ONLY;

        static List<Mode> parse(String value) {
            return switch (value) {
                case "hybrid" -> List.of(HYBRID);
                case "model-only" -> List.of(MODEL_ONLY);
                case "both" -> List.of(HYBRID, MODEL_ONLY);
                default -> throw new IllegalArgumentException("evaluation.mode must be hybrid, model-only or both");
            };
        }
    }

    static List<UtteranceEvaluationReport> repeat(UtteranceClassifier classifier,
                                                 List<UtteranceEvaluationCase> cases, int repetitions,
                                                 java.util.function.BiConsumer<Integer, UtteranceEvaluationReport> sink) {
        if (repetitions < 1) {
            throw new IllegalArgumentException("evaluation.repetitions must be positive");
        }
        List<UtteranceEvaluationReport> reports = new ArrayList<>();
        for (int repetition = 1; repetition <= repetitions; repetition++) {
            var report = new UtteranceCorpusEvaluator().evaluate(classifier, cases);
            reports.add(report);
            sink.accept(repetition, report);
        }
        return List.copyOf(reports);
    }

    // Errors are an outcome of their own, distinct from a successful OTHER decision.
    static double instability(List<UtteranceEvaluationReport> reports) {
        if (reports.isEmpty()) {
            throw new IllegalArgumentException("At least one report is required");
        }
        Map<String, Set<String>> outcomes = new LinkedHashMap<>();
        for (var report : reports) {
            for (var result : report.getResults()) {
                outcomes.computeIfAbsent(result.getEvaluationCase().getId(), ignored -> new HashSet<>())
                        .add(result.isValid() ? result.getActual().name() : "ERROR");
            }
        }
        return (double) outcomes.values().stream().filter(values -> values.size() > 1).count() / outcomes.size();
    }

    static Map<String, Object> metrics(UtteranceEvaluationReport report) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("cases", report.getResults().size());
        values.put("accuracy", report.accuracy());
        values.put("macroF1", report.macroF1());
        values.put("errors", report.errorCount());
        double criticalAccuracy = report.taggedAccuracy("critical");
        values.put("criticalAccuracy", Double.isNaN(criticalAccuracy) ? null : criticalAccuracy);
        values.put("otherFalseActivation", report.otherFalseActivationRate());
        values.put("followUpWithoutContext", report.followUpWithoutContextCount());
        values.put("p50Millis", report.percentileLatencyMillis(0.50));
        values.put("p95Millis", report.percentileLatencyMillis(0.95));
        values.put("confusionMatrix", report.confusionMatrix());
        Map<String, Object> labels = new LinkedHashMap<>();
        for (var label : com.fuad.enums.UtteranceDecision.values()) {
            labels.put(label.name(), Map.of("precision", report.precision(label), "recall", report.recall(label),
                    "f1", report.f1(label), "support", report.expectedCount(label)));
        }
        values.put("labels", labels);
        return values;
    }

    static Map<String, Object> artifact(Mode mode, int repetition, UtteranceEvaluationReport report,
                                        Map<String, Object> metadata) {
        Map<String, Object> document = new LinkedHashMap<>(metadata);
        document.put("mode", mode.name());
        document.put("repetition", repetition);
        document.put("metrics", metrics(report));
        UtteranceShapeDetector detector = new UtteranceShapeDetector();
        List<Map<String, Object>> cases = new ArrayList<>();
        Map<String, List<UtteranceEvaluationResult>> groups = new LinkedHashMap<>();
        for (var result : report.getResults()) {
            String route = mode == Mode.HYBRID && detector.classify(result.getEvaluationCase()
                    .toClassificationRequest()).isPresent() ? "rules" : "model";
            groups.computeIfAbsent(route, ignored -> new ArrayList<>()).add(result);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", result.getEvaluationCase().getId());
            row.put("expected", result.getEvaluationCase().expectedDecision());
            row.put("actual", result.getActual());
            row.put("error", result.getError());
            row.put("route", route);
            row.put("latencyMillis", result.latencyMillis());
            cases.add(row);
        }
        document.put("results", cases);
        Map<String, Object> routes = new LinkedHashMap<>();
        groups.forEach((route, results) -> routes.put(route, metrics(new UtteranceEvaluationReport(results))));
        document.put("byRoute", routes);
        return document;
    }

    static void write(Path file, Object document) {
        try {
            Files.writeString(file, new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(document),
                    StandardOpenOption.CREATE_NEW);
        }
        catch (IOException e) {
            throw new IllegalStateException("Unable to write evaluation report: " + file, e);
        }
    }
}

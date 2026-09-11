package com.fuad.evaluation.classification;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class DecisionEvaluationReport {
    private final List<String> labels;
    private final List<DecisionEvaluationResult> results;

    public DecisionEvaluationReport(List<String> labels, List<DecisionEvaluationResult> results) {
        if (labels == null || labels.isEmpty()) {
            throw new IllegalArgumentException("labels cannot be null or empty");
        }
        if (results == null || results.isEmpty()) {
            throw new IllegalArgumentException("results cannot be null or empty");
        }
        this.labels = labels.stream().map(value -> value.toLowerCase(Locale.ROOT)).toList();
        this.results = List.copyOf(results);
    }

    public long correctCount() {
        return results.stream().filter(DecisionEvaluationResult::isCorrect).count();
    }

    public long errorCount() {
        return results.stream().filter(result -> !result.isValid()).count();
    }

    public double accuracy() {
        return ratio(correctCount(), results.size());
    }

    public long expectedCount(String label) {
        return results.stream().filter(result -> result.testCase().normalizedExpected().equals(label)).count();
    }

    public long predictedCount(String label) {
        return results.stream().filter(result -> label.equals(result.actual())).count();
    }

    public long truePositiveCount(String label) {
        return results.stream().filter(result -> result.testCase().normalizedExpected().equals(label))
                .filter(result -> label.equals(result.actual())).count();
    }

    public double precision(String label) {
        return ratio(truePositiveCount(label), predictedCount(label));
    }

    public double recall(String label) {
        return ratio(truePositiveCount(label), expectedCount(label));
    }

    public double f1(String label) {
        double precision = precision(label);
        double recall = recall(label);
        return precision + recall == 0.0 ? 0.0 : 2.0 * precision * recall / (precision + recall);
    }

    public double macroF1() {
        return labels.stream().mapToDouble(this::f1).average().orElse(0.0);
    }

    public long firstPassValidCount() {
        return results.stream().filter(DecisionEvaluationResult::isValid)
                .filter(result -> result.attempts() == 1).count();
    }

    public long retryCount() {
        return results.stream().filter(result -> result.attempts() > 1).count();
    }

    public long retryRecoveredCount() {
        return results.stream().filter(DecisionEvaluationResult::isValid)
                .filter(result -> result.attempts() > 1).count();
    }

    public double retryRate() {
        return ratio(retryCount(), results.size());
    }

    public Map<String, Map<String, Long>> confusionMatrix() {
        Map<String, Map<String, Long>> matrix = new LinkedHashMap<>();
        for (String expected : labels) {
            Map<String, Long> row = new LinkedHashMap<>();
            for (String actual : labels) {
                long count = results.stream().filter(DecisionEvaluationResult::isValid)
                        .filter(result -> expected.equals(result.testCase().normalizedExpected()))
                        .filter(result -> actual.equals(result.actual())).count();
                row.put(actual, count);
            }
            matrix.put(expected, Map.copyOf(row));
        }
        return Map.copyOf(matrix);
    }

    public double percentileLatencyMillis(double percentile) {
        if (percentile < 0.0 || percentile > 1.0) {
            throw new IllegalArgumentException("percentile must be between 0 and 1");
        }
        List<Long> sorted = results.stream().map(DecisionEvaluationResult::latencyNanos).sorted().toList();
        int index = Math.max((int) Math.ceil(percentile * sorted.size()) - 1, 0);
        return sorted.get(index) / 1_000_000.0;
    }

    public List<DecisionEvaluationResult> failures() {
        return results.stream().filter(result -> !result.isCorrect()).toList();
    }

    public String format(String title, String metadata) {
        StringBuilder output = new StringBuilder(System.lineSeparator()).append(title)
                .append(System.lineSeparator()).append(metadata).append(System.lineSeparator())
                .append(String.format(Locale.ROOT,
                        "cases=%d correct=%d errors=%d accuracy=%.4f macroF1=%.4f p50=%.2fms p95=%.2fms%n",
                        results.size(), correctCount(), errorCount(), accuracy(), macroF1(),
                        percentileLatencyMillis(.50), percentileLatencyMillis(.95)));
        output.append(String.format(Locale.ROOT,
                "firstPassValid=%d retryCount=%d retryRecovered=%d retryRate=%.4f%n",
                firstPassValidCount(), retryCount(), retryRecoveredCount(), retryRate()));
        for (String label : labels) {
            output.append(String.format(Locale.ROOT,
                    "%s precision=%.4f recall=%.4f f1=%.4f support=%d%n",
                    label, precision(label), recall(label), f1(label), expectedCount(label)));
        }
        output.append("confusionMatrix=").append(confusionMatrix()).append(System.lineSeparator());
        for (DecisionEvaluationResult failure : failures()) {
            output.append("- ").append(failure.testCase().id()).append(" expected=")
                    .append(failure.testCase().normalizedExpected()).append(" actual=")
                    .append(failure.actual()).append(failure.error() == null ? "" : " error=" + failure.error())
                    .append(System.lineSeparator());
        }
        return output.toString();
    }

    private double ratio(long numerator, long denominator) {
        return denominator == 0 ? 0.0 : (double) numerator / denominator;
    }
}

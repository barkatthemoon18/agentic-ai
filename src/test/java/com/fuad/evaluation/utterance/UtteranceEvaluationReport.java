package com.fuad.evaluation.utterance;

import com.fuad.enums.UtteranceDecision;
import lombok.Getter;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Getter
public class UtteranceEvaluationReport {
    private final List<UtteranceEvaluationResult> results;

    public UtteranceEvaluationReport(List<UtteranceEvaluationResult> results) {
        if (results == null || results.isEmpty()) {
            throw new IllegalArgumentException("results cannot be null or empty");
        }
        this.results = List.copyOf(results);
    }

    public long correctCount() {
        return results.stream().filter(UtteranceEvaluationResult::isCorrect).count();
    }

    public long errorCount() {
        return results.stream().filter(result -> !result.isValid()).count();
    }

    public double accuracy() {
        return ratio(correctCount(), results.size());
    }

    public long expectedCount(UtteranceDecision decision) {
        return results.stream()
                .filter(result -> result.getEvaluationCase().expectedDecision() == decision)
                .count();
    }

    public long predictedCount(UtteranceDecision decision) {
        return results.stream().filter(result -> result.getActual() == decision).count();
    }

    public long truePositiveCount(UtteranceDecision decision) {
        return results.stream()
                .filter(result -> result.getEvaluationCase().expectedDecision() == decision)
                .filter(result -> result.getActual() == decision)
                .count();
    }

    public double precision(UtteranceDecision decision) {
        return ratio(truePositiveCount(decision), predictedCount(decision));
    }

    public double recall(UtteranceDecision decision) {
        return ratio(truePositiveCount(decision), expectedCount(decision));
    }

    public double f1(UtteranceDecision decision) {
        double precision = precision(decision);
        double recall = recall(decision);
        return precision + recall == 0.0 ? 0.0 : 2.0 * precision * recall / (precision + recall);
    }

    public double macroF1() {
        return Arrays.stream(UtteranceDecision.values())
                .mapToDouble(this::f1)
                .average()
                .orElse(0.0);
    }

    public long followUpWithoutContextCount() {
        return results.stream()
                .filter(result -> result.getActual() == UtteranceDecision.FOLLOW_UP)
                .filter(result -> !result.getEvaluationCase().getContextAvailable())
                .count();
    }

    public double otherFalseActivationRate() {
        long otherCount = expectedCount(UtteranceDecision.OTHER);
        long falseActivations = results.stream()
                .filter(result -> result.getEvaluationCase().expectedDecision() == UtteranceDecision.OTHER)
                .filter(UtteranceEvaluationResult::isValid)
                .filter(result -> result.getActual() != UtteranceDecision.OTHER)
                .count();
        return ratio(falseActivations, otherCount);
    }

    public double taggedAccuracy(String tag) {
        List<UtteranceEvaluationResult> tagged = results.stream()
                .filter(result -> result.getEvaluationCase().hasTag(tag))
                .toList();
        if (tagged.isEmpty()) {
            return Double.NaN;
        }
        return ratio(tagged.stream().filter(UtteranceEvaluationResult::isCorrect).count(), tagged.size());
    }

    public double percentileLatencyMillis(double percentile) {
        if (percentile < 0.0 || percentile > 1.0) {
            throw new IllegalArgumentException("percentile must be between 0 and 1");
        }
        List<Long> sorted = results.stream()
                .map(UtteranceEvaluationResult::getLatencyNanos)
                .sorted()
                .toList();
        int index = (int) Math.ceil(percentile * sorted.size()) - 1;
        return sorted.get(Math.max(index, 0)) / 1_000_000.0;
    }

    public Map<UtteranceDecision, Map<UtteranceDecision, Long>> confusionMatrix() {
        Map<UtteranceDecision, Map<UtteranceDecision, Long>> matrix = new EnumMap<>(UtteranceDecision.class);
        for (UtteranceDecision expected : UtteranceDecision.values()) {
            Map<UtteranceDecision, Long> row = new EnumMap<>(UtteranceDecision.class);
            for (UtteranceDecision actual : UtteranceDecision.values()) {
                long count = results.stream()
                        .filter(UtteranceEvaluationResult::isValid)
                        .filter(result -> result.getEvaluationCase().expectedDecision() == expected)
                        .filter(result -> result.getActual() == actual)
                        .count();
                row.put(actual, count);
            }
            matrix.put(expected, Map.copyOf(row));
        }
        return Map.copyOf(matrix);
    }

    public List<UtteranceEvaluationResult> failures() {
        return results.stream().filter(result -> !result.isCorrect()).toList();
    }

    public String format() {
        StringBuilder output = new StringBuilder();
        output.append(System.lineSeparator()).append("Utterance corpus evaluation").append(System.lineSeparator());
        output.append(String.format(Locale.ROOT,
                "cases=%d correct=%d errors=%d accuracy=%.4f macroF1=%.4f p50=%.2fms p95=%.2fms%n",
                results.size(), correctCount(), errorCount(), accuracy(), macroF1(),
                percentileLatencyMillis(0.50), percentileLatencyMillis(0.95)));
        for (UtteranceDecision decision : UtteranceDecision.values()) {
            output.append(String.format(Locale.ROOT,
                    "%s precision=%.4f recall=%.4f f1=%.4f support=%d%n",
                    decision, precision(decision), recall(decision), f1(decision), expectedCount(decision)));
        }
        output.append("confusionMatrix=").append(confusionMatrix()).append(System.lineSeparator());
        if (!failures().isEmpty()) {
            output.append("failures:").append(System.lineSeparator());
            for (UtteranceEvaluationResult result : failures()) {
                output.append("- ").append(result.getEvaluationCase().getId())
                        .append(" expected=").append(result.getEvaluationCase().expectedDecision())
                        .append(" actual=").append(result.getActual())
                        .append(result.getError() == null ? "" : " error=" + result.getError())
                        .append(System.lineSeparator());
            }
        }
        return output.toString();
    }

    private double ratio(long numerator, long denominator) {
        return denominator == 0 ? 0.0 : (double) numerator / denominator;
    }
}

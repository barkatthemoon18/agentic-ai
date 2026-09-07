package com.fuad.evaluation.utterance;

import com.fuad.activation.utterance.LocalUtteranceClassifier;
import com.fuad.config.AppConfig;
import com.fuad.enums.UtteranceDecision;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import com.fuad.activation.utterance.UtteranceClassificationRequest;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("model-evaluation")
class LocalUtteranceCorpusTest {

    @Test
    void classifierShouldMeetCorpusThresholds() throws Exception {
        String corpusName = System.getProperty("evaluation.corpus", "development");
        String resource = switch (corpusName) {
            case "development", "holdout" -> "evaluation/utterance-" + corpusName + ".jsonl";
            default -> throw new IllegalArgumentException("evaluation.corpus must be development or holdout");
        };
        var modes = UtteranceComparison.Mode.parse(System.getProperty("evaluation.mode", "hybrid"));
        int repetitions = integerProperty("evaluation.repetitions", 1);
        if (repetitions < 1) {
            throw new IllegalArgumentException("evaluation.repetitions must be positive");
        }
        List<UtteranceEvaluationCase> cases = new UtteranceCorpusLoader().loadResource(resource);
        String model = System.getProperty("evaluation.model", AppConfig.LOCAL_MODEL_ID);
        String baseUrl = System.getProperty("evaluation.base-url", AppConfig.LOCAL_AI_BASE_URL);
        Path root = Path.of("target", "model-evaluation");
        Files.createDirectories(root);
        Path directory = Files.createTempDirectory(root, corpusName + "-");
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("startedAt", Instant.now().toString());
        metadata.put("corpus", corpusName);
        metadata.put("model", model);
        metadata.put("temperature", 0.0);
        metadata.put("maxCompletionTokens", 8);
        metadata.put("repetitions", repetitions);
        var promptField = LocalUtteranceClassifier.class.getDeclaredField("SYSTEM_PROMPT");
        promptField.setAccessible(true);
        String prompt = (String) promptField.get(null);
        metadata.put("promptSha256", hash(prompt.getBytes(StandardCharsets.UTF_8)));
        Files.writeString(directory.resolve("prompt.txt"), prompt, StandardOpenOption.CREATE_NEW);
        try (var stream = getClass().getClassLoader().getResourceAsStream(resource)) {
            metadata.put("corpusSha256", hash(Objects.requireNonNull(stream).readAllBytes()));
        }
        OpenAIClient client = OpenAIOkHttpClient.builder()
                .baseUrl(baseUrl)
                .apiKey(System.getProperty("evaluation.api-key", AppConfig.LOCAL_AI_API_KEY))
                .timeout(Duration.ofSeconds(60)).maxRetries(0).build();
        try {
            try {
                if (client.models().list().data().stream().noneMatch(value -> value.id().equals(model))) {
                    throw new IllegalStateException("Configured model is not exposed: " + model);
                }
            }
            catch (RuntimeException e) {
                UtteranceComparison.write(directory.resolve("infrastructure-error.json"),
                        Map.of("metadata", metadata, "error", e.toString()));
                throw new IllegalStateException("Evaluation infrastructure unavailable; no corpus measured", e);
            }
            List<Executable> gates = new ArrayList<>();
            for (var mode : modes) {
                var classifier = new LocalUtteranceClassifier(client, model, mode == UtteranceComparison.Mode.HYBRID);
                long started = System.nanoTime();
                try {
                    // This ambient statement reaches the model in both variants and is outside either corpus.
                    var decision = classifier.classify(UtteranceClassificationRequest.withoutContext(
                            "La taza está junto al cuaderno."));
                    UtteranceComparison.write(directory.resolve(mode + "-warmup.json"),
                            Map.of("metadata", metadata, "mode", mode.name(), "decision", decision,
                                    "latencyMillis", (System.nanoTime() - started) / 1_000_000.0));
                }
                catch (RuntimeException e) {
                    UtteranceComparison.write(directory.resolve(mode + "-warmup-error.json"),
                            Map.of("metadata", metadata, "error", e.toString()));
                    throw new IllegalStateException("Warmup failed; corpus not started for " + mode, e);
                }
                var reports = UtteranceComparison.repeat(classifier, cases, repetitions, (repetition, report) -> {
                    UtteranceComparison.write(directory.resolve(mode + "-" + repetition + ".json"),
                            UtteranceComparison.artifact(mode, repetition, report, metadata));
                    System.out.println("model=" + model + " mode=" + mode + " repetition=" + repetition);
                    System.out.println(report.format());
                    if (mode == UtteranceComparison.Mode.HYBRID && !Boolean.getBoolean("evaluation.report-only")) {
                        gates.add(() -> assertThresholds(report, cases.size()));
                    }
                });
                UtteranceComparison.write(directory.resolve(mode + "-stability.json"),
                        Map.of("metadata", metadata, "mode", mode.name(),
                                "unstableCaseRate", UtteranceComparison.instability(reports),
                                "stabilityMeasured", repetitions > 1));
            }
            System.out.println("Reports: " + directory.toAbsolutePath());
            assertAll("Hybrid acceptance thresholds for every repetition", gates);
        }
        finally {
            client.close();
        }
    }

    private static String hash(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private void assertThresholds(UtteranceEvaluationReport report, int caseCount) {
        double minimumMacroF1 = doubleProperty("evaluation.minimum-macro-f1", 0.90);
        double minimumRecall = doubleProperty("evaluation.minimum-recall", 0.85);
        double maximumOtherFalseActivation = doubleProperty(
                "evaluation.maximum-other-false-activation", 0.02);
        int minimumCases = integerProperty("evaluation.minimum-cases", 30);
        double criticalAccuracy = report.taggedAccuracy("critical");
        String diagnostics = report.format();

        assertAll(
                () -> assertTrue(caseCount >= minimumCases,
                        "Corpus requires at least " + minimumCases + " cases"),
                () -> assertEquals(0, report.errorCount(), diagnostics),
                () -> assertEquals(0, report.followUpWithoutContextCount(), diagnostics),
                () -> assertTrue(report.macroF1() >= minimumMacroF1, diagnostics),
                () -> assertTrue(report.otherFalseActivationRate() <= maximumOtherFalseActivation, diagnostics),
                () -> assertTrue(!Double.isNaN(criticalAccuracy),
                        "Corpus requires at least one case tagged 'critical'"),
                () -> assertEquals(1.0, criticalAccuracy, 0.0001, diagnostics),
                () -> assertMinimumRecall(report, UtteranceDecision.NEW_REQUEST, minimumRecall, diagnostics),
                () -> assertMinimumRecall(report, UtteranceDecision.FOLLOW_UP, minimumRecall, diagnostics),
                () -> assertMinimumRecall(report, UtteranceDecision.OTHER, minimumRecall, diagnostics)
        );
    }

    private void assertMinimumRecall(UtteranceEvaluationReport report, UtteranceDecision decision,
                                     double minimumRecall, String diagnostics) {
        assertTrue(report.expectedCount(decision) > 0,
                "Corpus contains no expected cases for " + decision);
        assertTrue(report.recall(decision) >= minimumRecall, diagnostics);
    }

    private double doubleProperty(String name, double defaultValue) {
        return Double.parseDouble(System.getProperty(name, Double.toString(defaultValue)));
    }

    private int integerProperty(String name, int defaultValue) {
        return Integer.parseInt(System.getProperty(name, Integer.toString(defaultValue)));
    }
}

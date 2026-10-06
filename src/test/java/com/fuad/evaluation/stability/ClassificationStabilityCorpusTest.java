package com.fuad.evaluation.stability;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuad.config.AppConfig;
import com.fuad.evaluation.classification.DecisionCorpusCase;
import com.fuad.evaluation.classification.DecisionCorpusLoader;
import com.fuad.model.LocalModelOutput;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("model-evaluation")
class ClassificationStabilityCorpusTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> GENERAL = List.of("qwen_local", "gpt");
    private static final List<String> BACKEND = List.of("qwen_local", "gpt_web");
    private static final List<String> DEPTH = List.of("quick", "deep");
    private static final List<String> PLAN = List.of("knowledge_quick", "knowledge_deep", "web_quick", "web_deep");
    private static final List<String> ROUTING = List.of("system-time", "audio-control", "os-command", "current-research", "general");

    record Suite(String name, String dimension, List<String> labels, List<DecisionCorpusCase> cases) {}

    @Test
    void compareOriginalProductionVariantsWithoutChangingTheirClassifiers() throws Exception {
        int repetitions = Integer.getInteger("evaluation.repetitions", 5);
        assertTrue(repetitions > 0, "repetitions must be positive");
        String model = System.getProperty("evaluation.model", AppConfig.LOCAL_MODEL_ID);
        Path output = Path.of(System.getProperty("evaluation.output-directory"));
        Files.createDirectories(output);
        List<Suite> suites = suites();
        Map<String, Path> variants = new LinkedHashMap<>();
        String singleVariant = System.getProperty("evaluation.variant");
        if (singleVariant != null) {
            variants.put(singleVariant, Path.of(System.getProperty("evaluation.classes-directory", "target/classes")));
        } else {
            variants.put("pre-merge", Path.of(System.getProperty("evaluation.reference-pre")));
            variants.put("dev-merged", Path.of(System.getProperty("evaluation.reference-dev")));
            variants.put("candidate", Path.of("target/classes"));
        }
        List<String> gateFailures = new ArrayList<>();
        int concurrency = Integer.getInteger("evaluation.concurrency", 1);
        assertTrue(concurrency > 0, "concurrency must be positive");
        var workers = java.util.concurrent.Executors.newFixedThreadPool(concurrency);
        var realClient = OpenAIOkHttpClient.builder()
                .baseUrl(System.getProperty("evaluation.base-url", AppConfig.LOCAL_AI_BASE_URL))
                .apiKey(System.getProperty("evaluation.api-key", AppConfig.LOCAL_AI_API_KEY))
                .timeout(Duration.ofSeconds(60)).maxRetries(0).build();
        try {
            try {
                assertTrue(realClient.models().list().data().stream().anyMatch(value -> value.id().equals(model)),
                        "Configured model is unavailable: " + model);
            } catch (Throwable error) {
                write(output.resolve("infrastructure-error.json"), Map.of("error", error.toString()));
                throw error;
            }
            // Interleave variants by repetition to limit drift from machine load and session time.
            for (int repetition = 1; repetition <= repetitions; repetition++) {
                final int repetitionNumber = repetition;
                for (var entry : variants.entrySet()) {
                    Path directory = output.resolve(entry.getKey());
                    Files.createDirectories(directory);
                    InferenceTrace trace = new InferenceTrace(realClient);
                    try (var variant = new ProductionVariant(entry.getValue(), trace.client, model)) {
                        if (repetition == 1) {
                            Map<String, String> hashes = new LinkedHashMap<>();
                            for (var prompt : variant.prompts().entrySet()) {
                                Files.writeString(directory.resolve(prompt.getKey() + ".txt"), prompt.getValue());
                                hashes.put(prompt.getKey(), hash(prompt.getValue().getBytes(StandardCharsets.UTF_8)));
                            }
                            write(directory.resolve("prompt-hashes.json"), hashes);
                            write(directory.resolve("compatibility.json"), Map.of(
                                    "classesDirectory", entry.getValue().toAbsolutePath().toString(),
                                    "loader", "child-first com.fuad; no fallback to current production classes",
                                    "researchContract", variant.usesPlan() ? "ResearchPlan(access,depth)"
                                            : "original separate backend and depth classifiers",
                                    "inheritedBackend", variant.usesPlan() ? "recorded but not passed; original API has no such input"
                                            : "passed to original backend classifier",
                                    "productionChanges", "none",
                                    "newContractGold", "only candidate is required to pass strict General cases"));
                            for (String warmup : List.of("general", "routing", "depth")) {
                                trace.reset();
                                if (warmup.equals("general")) variant.general("¿Qué es una brújula?");
                                else if (warmup.equals("routing")) variant.routing("Explícame una brújula", false);
                                else variant.research("¿Qué es una brújula?", null, "depth");
                                write(directory.resolve(warmup + "-warmup.json"), trace.snapshot());
                            }
                        }
                        for (Suite suite : suites) {
                            List<Map<String, Object>> rows = new ArrayList<>();
                            Path rowFile = directory.resolve(suite.name() + "-r" + repetition + ".jsonl");
                            if (Boolean.getBoolean("evaluation.resume") && Files.exists(rowFile)) {
                                for (String line : Files.readAllLines(rowFile)) rows.add(JSON.readValue(line,
                                        JSON.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class)));
                                if (rows.size() != suite.cases().size()) rows.clear();
                            }
                            List<java.util.concurrent.Future<Map<String, Object>>> pending = new ArrayList<>();
                            if (rows.isEmpty()) for (DecisionCorpusCase testCase : suite.cases()) pending.add(workers.submit(() -> {
                                InferenceTrace caseTrace = new InferenceTrace(realClient);
                                try (var caseVariant = new ProductionVariant(entry.getValue(), caseTrace.client, model)) {
                                    return evaluate(caseVariant, caseTrace, suite, testCase, entry.getKey(), repetitionNumber);
                                }
                            }));
                            if (!pending.isEmpty()) try (var writer = Files.newBufferedWriter(rowFile, StandardCharsets.UTF_8)) {
                                for (var pendingRow : pending) {
                                    Map<String, Object> row = pendingRow.get();
                                    rows.add(row);
                                    writer.write(JSON.writeValueAsString(row));
                                    writer.newLine();
                                    writer.flush();
                                    if (rows.size() % 10 == 0) System.out.printf("PROGRESS %s r%d %s %d/%d%n",
                                            entry.getKey(), repetition, suite.name(), rows.size(), suite.cases().size());
                                }
                            }
                            Map<String, Object> metrics = StabilityMetrics.metrics(rows, suite.labels());
                            boolean passed = ((Number) metrics.get("errors")).longValue() == 0
                                    && (suite.dimension().startsWith("routing") ? suite.dimension().equals("routing-raw")
                                        || ((Number) metrics.get("accuracy")).doubleValue() >= .90
                                        : suite.dimension().equals("follow-up")
                                            ? ((Number) metrics.get("accuracy")).doubleValue() == 1.0
                                            : ((Number) metrics.get("macroF1")).doubleValue() >= .90);
                            if (strict(suite))
                                passed = rows.stream().allMatch(StabilityMetrics::correct);
                            metrics.put("gatePassed", passed);
                            metrics.put("gateApplies", !suite.dimension().equals("routing-raw")
                                    && !suite.dimension().equals("backend")
                                    && !(strict(suite) && List.of("pre-merge", "dev-merged").contains(entry.getKey())));
                            addPlanMetrics(metrics, rows, suite);
                            write(directory.resolve(suite.name() + "-r" + repetition + "-metrics.json"), metrics);
                            if (!passed && Boolean.TRUE.equals(metrics.get("gateApplies")))
                                gateFailures.add(entry.getKey() + "/" + suite.name() + "/r" + repetition);
                            System.out.printf("EVALUATION %s r%d %s cases=%d accuracy=%.4f macroF1=%.4f errors=%s gate=%s%n",
                                    entry.getKey(), repetition, suite.name(), rows.size(), metrics.get("accuracy"),
                                    metrics.get("macroF1"), metrics.get("errors"), passed);
                        }
                    }
                }
            }
        }
        finally { workers.shutdownNow(); realClient.close(); }
        for (String variant : variants.keySet()) for (Suite suite : suites) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (int repetition = 1; repetition <= repetitions; repetition++)
                for (String line : Files.readAllLines(output.resolve(variant).resolve(suite.name() + "-r" + repetition + ".jsonl")))
                    rows.add(JSON.readValue(line, JSON.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class)));
            List<Map<String, Object>> stability = StabilityMetrics.stability(rows, repetitions);
            Map<String, Object> aggregate = StabilityMetrics.metrics(rows, suite.labels());
            aggregate.put("stabilityMeasured", repetitions > 1);
            aggregate.put("unstableCases", stability.stream().filter(row -> row.get("classification").equals("unstable")).count());
            aggregate.put("deterministicRegressions", stability.stream().filter(row -> row.get("classification").equals("deterministic-regression")).count());
            aggregate.put("strictAllCasesPass", stability.stream().allMatch(row -> Boolean.TRUE.equals(row.get("strictPass"))));
            addPlanMetrics(aggregate, rows, suite);
            write(output.resolve(variant).resolve(suite.name() + "-aggregate.json"), aggregate);
            write(output.resolve(variant).resolve(suite.name() + "-stability.json"), stability);
        }
        write(output.resolve(singleVariant == null ? "gate-failures.json" : singleVariant + "-gate-failures.json"), gateFailures);
        if (!Boolean.getBoolean("evaluation.report-only")) assertTrue(gateFailures.isEmpty(), gateFailures.toString());
    }

    private Map<String, Object> evaluate(ProductionVariant variant, InferenceTrace trace, Suite suite,
                                         DecisionCorpusCase testCase, String name, int repetition) {
        trace.reset();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("variant", name); row.put("suite", suite.name()); row.put("repetition", repetition);
        row.put("id", testCase.id()); row.put("query", testCase.query()); row.put("expected", testCase.expected());
        row.put("tags", testCase.tags()); row.put("rationale", testCase.rationale());
        row.put("inheritedBackend", testCase.inheritedBackend());
        row.put("inheritedBackendPassed", !variant.usesPlan() && suite.dimension().equals("backend"));
        row.put("error", null); row.put("actual", null);
        long start = System.nanoTime();
        try {
            String actual;
            if (suite.dimension().equals("general")) actual = variant.general(testCase.query());
            else if (suite.dimension().startsWith("routing")) actual = variant.routing(testCase.query(), suite.dimension().equals("routing-guarded"));
            else if (suite.dimension().equals("follow-up")) actual = variant.followUp(testCase.query(),
                    testCase.tags().contains("owner:general") ? "GENERAL" : "CURRENT_RESEARCH");
            else {
                Map<String, String> plan = variant.research(testCase.query(), testCase.inheritedBackend(), suite.dimension());
                row.put("researchDecision", plan);
                actual = suite.dimension().equals("backend") ? plan.get("access").equals("knowledge") ? "qwen_local" : "gpt_web"
                        : suite.dimension().equals("depth") ? plan.get("depth") : plan.get("access") + "_" + plan.get("depth");
            }
            row.put("actual", actual);
        } catch (Exception error) { row.put("error", error.toString()); }
        row.put("latencyMillis", (System.nanoTime() - start) / 1_000_000.0);
        List<Map<String, Object>> attempts = trace.snapshot();
        row.put("attempts", attempts.size()); row.put("inference", attempts);
        Set<String> rawLabels = Set.copyOf(suite.dimension().startsWith("routing") ? ROUTING
                : suite.dimension().equals("general") ? List.of("local", "gpt") : variant.usesPlan() ? PLAN : DEPTH);
        for (Map<String, Object> attempt : attempts) {
            boolean valid = false;
            if (attempt.get("error") == null) try {
                LocalModelOutput.extractLeadingLabel((String) attempt.get("output"), rawLabels, "evaluation attempt");
                valid = true;
            } catch (RuntimeException invalid) { /* Preserve the original output, including absent content. */ }
            attempt.put("outputValid", valid);
        }
        String rawLabel = null;
        if (!attempts.isEmpty()) try {
            rawLabel = LocalModelOutput.extractLeadingLabel((String) attempts.getLast().get("output"), rawLabels, "evaluation trace");
        } catch (RuntimeException invalid) { /* Invalid raw output is distinct from a fallback label. */ }
        row.put("lastModelLabel", rawLabel); row.put("lastModelOutputValid", rawLabel != null);
        String comparableRaw = rawLabel;
        if ("local".equals(rawLabel)) comparableRaw = "qwen_local";
        if (rawLabel != null && variant.usesPlan() && !suite.dimension().equals("general") && !suite.dimension().startsWith("routing")) {
            if (suite.dimension().equals("backend")) comparableRaw = rawLabel.startsWith("knowledge") ? "qwen_local" : "gpt_web";
            else if (suite.dimension().equals("depth")) comparableRaw = rawLabel.endsWith("quick") ? "quick" : "deep";
        }
        row.put("route", attempts.isEmpty() ? "rules" : row.get("error") != null ? "error"
                : rawLabel == null ? "fallback" : comparableRaw.equals(row.get("actual")) ? "model" : "rule-override");
        row.put("correct", StabilityMetrics.correct(row));
        return row;
    }

    private List<Suite> suites() throws Exception {
        List<Suite> suites = new ArrayList<>();
        for (String corpus : List.of("development", "holdout-v1", "holdout-v2", "contract-regression"))
            suites.add(resource("general-" + corpus, "general", GENERAL, "general-backend-" + corpus, Set.of()));
        suites.add(resource("general-boundary-regression", "general", GENERAL, "general-backend-boundary-regression", Set.of()));
        for (String corpus : List.of("development", "holdout")) {
            suites.add(resource("research-backend-" + corpus, "backend", BACKEND, "research-backend-" + corpus, Set.copyOf(BACKEND)));
            suites.add(resource("research-depth-" + corpus, "depth", DEPTH, "research-depth-" + corpus, Set.of()));
        }
        suites.add(resource("research-plan-diagnostic", "plan", PLAN, "research-plan-diagnostic", Set.of()));
        suites.add(resource("research-plan-development", "plan", PLAN, "research-plan-development", Set.of()));
        suites.add(resource("research-plan-regression", "plan", PLAN, "research-plan-regression", Set.of()));
        var field = Class.forName("com.fuad.evaluation.routing.LocalSemanticRouterCorpusTest").getDeclaredField("CASES");
        field.setAccessible(true);
        List<DecisionCorpusCase> routing = new ArrayList<>();
        for (Object existing : (List<?>) field.get(null)) {
            var id = existing.getClass().getDeclaredMethod("id"); id.setAccessible(true);
            var command = existing.getClass().getDeclaredMethod("command"); command.setAccessible(true);
            var expected = existing.getClass().getDeclaredMethod("expected"); expected.setAccessible(true);
            String label = ((Enum<?>) expected.invoke(existing)).name().toLowerCase().replace('_', '-');
            routing.add(new DecisionCorpusCase(id.invoke(existing).toString(), command.invoke(existing).toString(),
                    label, List.of("existing-routing"), "Original semantic routing corpus", null));
        }
        List<DecisionCorpusCase> joint = suites.stream().filter(s -> s.dimension().equals("plan")).findFirst().orElseThrow().cases();
        for (DecisionCorpusCase testCase : joint.subList(0, 2)) routing.add(new DecisionCorpusCase(testCase.id(),
                testCase.query(), "general", List.of("general-research-boundary"), "Exposition without web stays General", null));
        routing.add(new DecisionCorpusCase("cross-sources", "Dame las fuentes que encontraste", "current-research",
                List.of("reused", "general-research-boundary"), "Existing request for external sources", null));
        routing.add(new DecisionCorpusCase("cross-negated", "No lo busques en Internet, dime qué recuerdas", "general",
                List.of("reused", "general-research-boundary"), "Existing negated research transition", null));
        for (String mode : List.of("raw", "guarded")) suites.add(new Suite("routing-" + mode, "routing-" + mode, ROUTING, routing));
        for (String mode : List.of("raw", "guarded"))
            suites.add(resource("routing-boundary-" + mode, "routing-" + mode, ROUTING, "routing-boundary-regression", Set.of()));
        List<DecisionCorpusCase> follow = new ArrayList<>();
        for (String owner : List.of("general", "research")) for (DecisionCorpusCase testCase : routing.subList(21, routing.size())) {
            String expected = owner.equals("research") || testCase.id().equals("cross-sources") ? "current-research" : "general";
            follow.add(new DecisionCorpusCase(owner + "-" + testCase.id(), testCase.query(), expected,
                    List.of("owner:" + owner), "Existing owner and research escalation contract", null));
        }
        suites.add(new Suite("follow-up-crossings", "follow-up", List.of("general", "current-research"), follow));
        String selection = System.getProperty("evaluation.suites", "");
        if (!selection.isBlank()) {
            Set<String> selected = Set.of(selection.split(","));
            assertTrue(suites.stream().map(Suite::name).collect(java.util.stream.Collectors.toSet()).containsAll(selected),
                    "Unknown evaluation.suites: " + selection);
            suites = suites.stream().filter(s -> selected.contains(s.name())).toList();
        }
        for (Suite suite : suites) {
            Path effective = Path.of(System.getProperty("evaluation.output-directory"), "corpus", suite.name() + "-effective.json");
            String inputs = JSON.writerWithDefaultPrettyPrinter().writeValueAsString(suite.cases());
            if (Files.exists(effective)) assertTrue(inputs.equals(Files.readString(effective)), "Effective corpus changed: " + suite.name());
            else Files.writeString(effective, inputs, StandardCharsets.UTF_8);
        }
        return suites;
    }

    private Suite resource(String name, String dimension, List<String> labels, String resource, Set<String> inherited) throws Exception {
        String path = "evaluation/" + resource + ".jsonl";
        byte[] bytes;
        try (var input = getClass().getClassLoader().getResourceAsStream(path)) { bytes = java.util.Objects.requireNonNull(input, path).readAllBytes(); }
        Path frozen = Path.of(System.getProperty("evaluation.output-directory"), "corpus");
        Files.createDirectories(frozen);
        Path frozenCorpus = frozen.resolve(resource + ".jsonl");
        if (Files.exists(frozenCorpus)) assertTrue(java.util.Arrays.equals(bytes, Files.readAllBytes(frozenCorpus)),
                "Frozen corpus changed: " + path);
        else Files.write(frozenCorpus, bytes);
        Files.writeString(frozen.resolve(resource + ".sha256"), hash(bytes));
        return new Suite(name, dimension, labels, new DecisionCorpusLoader().loadResource(path, Set.copyOf(labels), inherited));
    }
    private static String hash(byte[] bytes) throws Exception {
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    private static boolean strict(Suite suite) {
        return suite.name().equals("general-contract-regression") || suite.name().equals("general-boundary-regression")
                || suite.name().equals("research-plan-regression") || suite.name().equals("research-plan-diagnostic")
                || suite.name().equals("routing-boundary-guarded") || suite.dimension().equals("follow-up");
    }

    private static void addPlanMetrics(Map<String, Object> metrics, List<Map<String, Object>> rows, Suite suite) {
        if (suite.dimension().equals("plan")) {
            metrics.put("access", StabilityMetrics.project(rows, List.of("knowledge", "web"), true));
            metrics.put("depth", StabilityMetrics.project(rows, DEPTH, false));
        }
    }
    private static void write(Path path, Object value) throws Exception {
        Files.writeString(path, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(value), StandardCharsets.UTF_8);
    }
}

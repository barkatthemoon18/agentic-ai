package com.fuad.evaluation.model;

import com.fuad.activation.wake.GraniteWakeClassifier;
import com.fuad.assistant.skills.audio.GraniteAudioControlParser;
import com.fuad.assistant.skills.os.GraniteOsCommandParser;
import com.fuad.audio.AudioControlIntent;
import com.fuad.config.AppConfig;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("model-evaluation")
class LocalStructuredClassifiersBenchmarkTest {

    @Test
    void structuredClassifiersShouldMeetBenchmarkThreshold() {
        String model = System.getProperty("evaluation.model", AppConfig.LOCAL_MODEL_ID);
        OpenAIClient client = OpenAIOkHttpClient.builder()
                .baseUrl(System.getProperty("evaluation.base-url", AppConfig.LOCAL_AI_BASE_URL))
                .apiKey(System.getProperty("evaluation.api-key", AppConfig.LOCAL_AI_API_KEY))
                .build();
        GraniteWakeClassifier wake = new GraniteWakeClassifier(client, model);
        GraniteOsCommandParser os = new GraniteOsCommandParser(client, model);
        GraniteAudioControlParser audio = new GraniteAudioControlParser(client, model);
        List<BenchmarkCase> cases = List.of(
                new BenchmarkCase("wake", "wake-01", "WAKE", () -> wake.classify("Oeres", "abre Spotify").name()),
                new BenchmarkCase("wake", "wake-02", "WAKE", () -> wake.classify("Oyares", "qué hora es").name()),
                new BenchmarkCase("wake", "wake-03", "SEMANTIC_INTENT",
                        () -> wake.classify("Sabes", "qué hora es").name()),
                new BenchmarkCase("wake", "wake-04", "NONE",
                        () -> wake.classify("Eres", "bastante rápido").name()),
                new BenchmarkCase("wake", "wake-05", "NONE",
                        () -> wake.classify("Las áreas", "están delimitadas").name()),

                new BenchmarkCase("os", "os-01", "OPEN_APPLICATION|spotify",
                        () -> formatOs(os.parse("Abre Spotify"))),
                new BenchmarkCase("os", "os-02", "CLOSE_APPLICATION|spotify",
                        () -> formatOs(os.parse("¿Puedes cerrar Spotify?"))),
                new BenchmarkCase("os", "os-03", "UNSUPPORTED|",
                        () -> formatOs(os.parse("Mañana voy a cerrar Spotify"))),
                new BenchmarkCase("os", "os-04", "UNSUPPORTED|",
                        () -> formatOs(os.parse("Spotify se cerró solo"))),

                new BenchmarkCase("audio", "audio-01", "SET_VOLUME|ASSISTANT|40",
                        () -> formatAudio(audio.parse("Pon tu volumen al 40%"))),
                new BenchmarkCase("audio", "audio-02", "MUTE|APPLICATION|null",
                        () -> formatAudio(audio.parse("Silencia Spotify"))),
                new BenchmarkCase("audio", "audio-03", "UNSUPPORTED|APPLICATION|null",
                        () -> formatAudio(audio.parse("¿Por qué Spotify no tiene sonido?"))),
                new BenchmarkCase("audio", "audio-04", "UNSUPPORTED|ASSISTANT|null",
                        () -> formatAudio(audio.parse("Habla más lento")))
        );
        List<BenchmarkResult> results = new ArrayList<>();

        for (BenchmarkCase benchmarkCase : cases) {
            long startedAt = System.nanoTime();
            try {
                results.add(new BenchmarkResult(benchmarkCase, benchmarkCase.call().get(), null,
                        System.nanoTime() - startedAt));
            }
            catch (RuntimeException exception) {
                results.add(new BenchmarkResult(benchmarkCase, null, exception,
                        System.nanoTime() - startedAt));
            }
        }

        long errors = results.stream().filter(result -> result.error() != null).count();
        long correct = results.stream().filter(BenchmarkResult::isCorrect).count();
        double accuracy = (double) correct / results.size();
        List<Long> latencies = results.stream().map(BenchmarkResult::latencyNanos)
                .sorted(Comparator.naturalOrder()).toList();
        String report = formatReport(model, results, correct, errors, accuracy,
                percentileMillis(latencies, 0.50), percentileMillis(latencies, 0.95));
        System.out.println(report);

        if (Boolean.getBoolean("evaluation.report-only")) {
            return;
        }
        double minimumAccuracy = Double.parseDouble(
                System.getProperty("evaluation.minimum-structured-accuracy", "0.85"));
        assertEquals(0, errors, report);
        assertTrue(accuracy >= minimumAccuracy, report);
    }

    private static String formatOs(com.fuad.assistant.skills.os.OsCommandIntent intent) {
        return intent.getAction() + "|" + intent.getTarget();
    }

    private static String formatAudio(AudioControlIntent intent) {
        return intent.getAudioAction() + "|" + intent.getAudioScope() + "|" + intent.getValue();
    }

    private String formatReport(String model, List<BenchmarkResult> results, long correct,
                                long errors, double accuracy, double p50, double p95) {
        StringBuilder report = new StringBuilder(System.lineSeparator())
                .append("Local structured classifiers benchmark").append(System.lineSeparator())
                .append("model=").append(model).append(System.lineSeparator())
                .append(String.format(Locale.ROOT,
                        "cases=%d correct=%d errors=%d accuracy=%.4f p50=%.2fms p95=%.2fms%n",
                        results.size(), correct, errors, accuracy, p50, p95));
        for (String component : List.of("wake", "os", "audio")) {
            List<BenchmarkResult> componentResults = results.stream()
                    .filter(result -> result.benchmarkCase().component().equals(component)).toList();
            long componentCorrect = componentResults.stream().filter(BenchmarkResult::isCorrect).count();
            report.append(String.format(Locale.ROOT, "%s=%d/%d%n",
                    component, componentCorrect, componentResults.size()));
        }
        results.stream().filter(result -> !result.isCorrect()).forEach(result -> report
                .append("- ").append(result.benchmarkCase().id())
                .append(" expected=").append(result.benchmarkCase().expected())
                .append(" actual=").append(result.actual())
                .append(result.error() == null ? "" : " error=" + result.error())
                .append(System.lineSeparator()));
        return report.toString();
    }

    private double percentileMillis(List<Long> sortedLatencies, double percentile) {
        int index = Math.max((int) Math.ceil(percentile * sortedLatencies.size()) - 1, 0);
        return sortedLatencies.get(index) / 1_000_000.0;
    }

    private record BenchmarkCase(String component, String id, String expected, Supplier<String> call) {
    }

    private record BenchmarkResult(BenchmarkCase benchmarkCase, String actual,
                                   RuntimeException error, long latencyNanos) {
        private boolean isCorrect() {
            return error == null && benchmarkCase.expected().equals(actual);
        }
    }
}

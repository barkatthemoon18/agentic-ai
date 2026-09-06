package com.fuad.evaluation.model;

import com.fuad.activation.wake.LocalWakeClassifier;
import com.fuad.assistant.skills.audio.LocalAudioControlParser;
import com.fuad.assistant.skills.os.LocalOsCommandParser;
import com.fuad.audio.AudioControlIntent;
import com.fuad.config.AppConfig;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
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
                .apiKey(System.getProperty("evaluation.api-key", AppConfig.LOCAL_AI_API_KEY)).build();
        LocalWakeClassifier wake = new LocalWakeClassifier(client, model);
        LocalOsCommandParser os = new LocalOsCommandParser(client, model);
        LocalAudioControlParser audio = new LocalAudioControlParser(client, model);
        List<Case> cases = List.of(
                new Case("wake", "wake-01", "WAKE", () -> wake.classify("Oeres", "abre Spotify").name()),
                new Case("wake", "wake-02", "WAKE", () -> wake.classify("Oyares", "qué hora es").name()),
                new Case("wake", "wake-03", "SEMANTIC_INTENT", () -> wake.classify("Sabes", "qué hora es").name()),
                new Case("wake", "wake-04", "NONE", () -> wake.classify("Eres", "bastante rápido").name()),
                new Case("wake", "wake-05", "NONE", () -> wake.classify("Las áreas", "están delimitadas").name()),
                new Case("os", "os-01", "OPEN_APPLICATION|spotify", () -> formatOs(os.parse("Abre Spotify"))),
                new Case("os", "os-02", "CLOSE_APPLICATION|spotify", () -> formatOs(os.parse("¿Puedes cerrar Spotify?"))),
                new Case("os", "os-03", "UNSUPPORTED|", () -> formatOs(os.parse("Mañana voy a cerrar Spotify"))),
                new Case("os", "os-04", "UNSUPPORTED|", () -> formatOs(os.parse("Spotify se cerró solo"))),
                new Case("audio", "audio-01", "SET_VOLUME|ASSISTANT|40", () -> formatAudio(audio.parse("Pon tu volumen al 40%"))),
                new Case("audio", "audio-02", "MUTE|APPLICATION|null", () -> formatAudio(audio.parse("Silencia Spotify"))),
                new Case("audio", "audio-03", "UNSUPPORTED|APPLICATION|null", () -> formatAudio(audio.parse("¿Por qué Spotify no tiene sonido?"))),
                new Case("audio", "audio-04", "UNSUPPORTED|ASSISTANT|null", () -> formatAudio(audio.parse("Habla más lento")))
        );
        List<Result> results = new ArrayList<>();
        for (Case testCase : cases) {
            long start = System.nanoTime();
            try {
                results.add(new Result(testCase, testCase.call().get(), null, System.nanoTime() - start));
            } catch (RuntimeException error) {
                results.add(new Result(testCase, null, error, System.nanoTime() - start));
            }
        }
        long errors = results.stream().filter(result -> result.error() != null).count();
        long correct = results.stream().filter(Result::isCorrect).count();
        double accuracy = (double) correct / results.size();
        List<Long> latency = results.stream().map(Result::latencyNanos).sorted().toList();
        StringBuilder report = new StringBuilder("\nLocal structured classifiers benchmark\n")
                .append("model=").append(model).append('\n')
                .append(String.format(Locale.ROOT,
                        "cases=%d correct=%d errors=%d accuracy=%.4f p50=%.2fms p95=%.2fms%n",
                        results.size(), correct, errors, accuracy, percentile(latency, .50), percentile(latency, .95)));
        for (String component : List.of("wake", "os", "audio")) {
            List<Result> group = results.stream().filter(r -> r.testCase().component().equals(component)).toList();
            report.append(component).append('=').append(group.stream().filter(Result::isCorrect).count())
                    .append('/').append(group.size()).append('\n');
        }
        results.stream().filter(result -> !result.isCorrect()).forEach(result -> report
                .append("- ").append(result.testCase().id()).append(" expected=")
                .append(result.testCase().expected()).append(" actual=").append(result.actual())
                .append(result.error() == null ? "" : " error=" + result.error()).append('\n'));
        System.out.println(report);
        if (!Boolean.getBoolean("evaluation.report-only")) {
            assertEquals(0, errors, report.toString());
            assertTrue(accuracy >= Double.parseDouble(
                    System.getProperty("evaluation.minimum-structured-accuracy", "0.85")), report.toString());
        }
    }

    private static String formatOs(com.fuad.assistant.skills.os.OsCommandIntent value) {
        return value.getAction() + "|" + value.getTarget();
    }
    private static String formatAudio(AudioControlIntent value) {
        return value.getAudioAction() + "|" + value.getAudioScope() + "|" + value.getValue();
    }
    private double percentile(List<Long> sorted, double percentile) {
        return sorted.get(Math.max((int) Math.ceil(percentile * sorted.size()) - 1, 0)) / 1_000_000.0;
    }
    private record Case(String component, String id, String expected, Supplier<String> call) {}
    private record Result(Case testCase, String actual, RuntimeException error, long latencyNanos) {
        private boolean isCorrect() { return error == null && testCase.expected().equals(actual); }
    }
}

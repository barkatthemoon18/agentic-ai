package com.fuad.evaluation.routing;

import com.fuad.assistant.routing.LocalSemanticRouter;
import com.fuad.assistant.routing.GuardedSemanticRouter;
import com.fuad.assistant.routing.SemanticRouter;
import com.fuad.config.AppConfig;
import com.fuad.enums.Capability;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("model-evaluation")
class LocalSemanticRouterCorpusTest {
    private static final List<RoutingCase> CASES = List.of(
            new RoutingCase("time-01", "¿Qué hora es?", Capability.SYSTEM_TIME),
            new RoutingCase("time-02", "Dime la hora", Capability.SYSTEM_TIME),
            new RoutingCase("time-03", "¿Qué fecha es hoy?", Capability.SYSTEM_TIME),
            new RoutingCase("time-04", "¿Qué día es hoy?", Capability.SYSTEM_TIME),
            new RoutingCase("audio-01", "Pon tu volumen al 40%", Capability.AUDIO_CONTROL),
            new RoutingCase("audio-02", "Habla más fuerte", Capability.AUDIO_CONTROL),
            new RoutingCase("audio-03", "Silencia Spotify", Capability.AUDIO_CONTROL),
            new RoutingCase("audio-04", "Baja el volumen de Windows", Capability.AUDIO_CONTROL),
            new RoutingCase("os-01", "Abre Spotify", Capability.OS_COMMAND),
            new RoutingCase("os-02", "¿Puedes cerrar Spotify?", Capability.OS_COMMAND),
            new RoutingCase("os-03", "Reinicia Spotify", Capability.OS_COMMAND),
            new RoutingCase("os-04", "¿Qué versión de Firefox tengo instalada?", Capability.OS_COMMAND),
            new RoutingCase("os-05", "¿Qué procesos están ejecutándose?", Capability.OS_COMMAND),
            new RoutingCase("research-01", "¿Cuál es la última versión disponible de Firefox?",
                    Capability.CURRENT_RESEARCH),
            new RoutingCase("research-02", "¿Cuál es el precio actual de Bitcoin?",
                    Capability.CURRENT_RESEARCH),
            new RoutingCase("research-03", "Busca las últimas noticias sobre OpenAI",
                    Capability.CURRENT_RESEARCH),
            new RoutingCase("research-04", "¿Qué ocurrió hoy con NVIDIA?", Capability.CURRENT_RESEARCH),
            new RoutingCase("general-01", "Spotify", Capability.GENERAL),
            new RoutingCase("general-02", "¿Quién fue Alan Turing?", Capability.GENERAL),
            new RoutingCase("general-03", "Explícame RSA", Capability.GENERAL),
            new RoutingCase("general-04", "¿Por qué Spotify se está cerrando solo?", Capability.GENERAL),
            new RoutingCase("general-05", "¿Qué día fue el 11 de septiembre de 2001?", Capability.GENERAL)
    );

    @Test
    void routerShouldMeetCorpusThreshold() {
        String model = System.getProperty("evaluation.model", AppConfig.LOCAL_MODEL_ID);
        OpenAIClient client = OpenAIOkHttpClient.builder()
                .baseUrl(System.getProperty("evaluation.base-url", AppConfig.LOCAL_AI_BASE_URL))
                .apiKey(System.getProperty("evaluation.api-key", AppConfig.LOCAL_AI_API_KEY)).build();
        SemanticRouter raw = new LocalSemanticRouter(client, model);
        boolean guarded = Boolean.parseBoolean(System.getProperty("evaluation.semantic-guarded", "true"));
        SemanticRouter router = guarded ? new GuardedSemanticRouter(raw) : raw;
        List<RoutingResult> results = new ArrayList<>();
        for (RoutingCase testCase : CASES) {
            long start = System.nanoTime();
            try {
                results.add(new RoutingResult(testCase, router.classify(testCase.command()), null,
                        System.nanoTime() - start));
            } catch (RuntimeException error) {
                results.add(new RoutingResult(testCase, null, error, System.nanoTime() - start));
            }
        }
        long errors = results.stream().filter(result -> result.error() != null).count();
        long correct = results.stream().filter(RoutingResult::isCorrect).count();
        double accuracy = (double) correct / results.size();
        List<Long> latency = results.stream().map(RoutingResult::latencyNanos).sorted().toList();
        String report = format(model, guarded, results, correct, errors, accuracy,
                percentile(latency, .50), percentile(latency, .95));
        System.out.println(report);
        if (!Boolean.getBoolean("evaluation.report-only")) {
            assertEquals(0, errors, report);
            assertTrue(accuracy >= Double.parseDouble(
                    System.getProperty("evaluation.minimum-routing-accuracy", "0.90")), report);
        }
    }

    private String format(String model, boolean guarded, List<RoutingResult> results,
                          long correct, long errors, double accuracy, double p50, double p95) {
        StringBuilder out = new StringBuilder("\nSemantic routing corpus evaluation\n")
                .append("model=").append(model).append(" guarded=").append(guarded).append('\n')
                .append(String.format(Locale.ROOT,
                        "cases=%d correct=%d errors=%d accuracy=%.4f p50=%.2fms p95=%.2fms%n",
                        results.size(), correct, errors, accuracy, p50, p95));
        results.stream().filter(result -> !result.isCorrect()).forEach(result -> out
                .append("- ").append(result.testCase().id()).append(" expected=")
                .append(result.testCase().expected()).append(" actual=").append(result.actual())
                .append(result.error() == null ? "" : " error=" + result.error()).append('\n'));
        return out.toString();
    }

    private double percentile(List<Long> sorted, double percentile) {
        return sorted.get(Math.max((int) Math.ceil(percentile * sorted.size()) - 1, 0)) / 1_000_000.0;
    }

    private record RoutingCase(String id, String command, Capability expected) {}
    private record RoutingResult(RoutingCase testCase, Capability actual, RuntimeException error,
                                 long latencyNanos) {
        private boolean isCorrect() { return error == null && testCase.expected() == actual; }
    }
}

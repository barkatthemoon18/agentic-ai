package com.fuad.evaluation.routing;

import com.fuad.assistant.routing.GraniteSemanticRouter;
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
        String baseUrl = System.getProperty("evaluation.base-url", AppConfig.LOCAL_AI_BASE_URL);
        String apiKey = System.getProperty("evaluation.api-key", AppConfig.LOCAL_AI_API_KEY);
        String model = System.getProperty("evaluation.model", AppConfig.LOCAL_MODEL_ID);
        boolean guarded = Boolean.parseBoolean(System.getProperty("evaluation.semantic-guarded", "true"));
        OpenAIClient client = OpenAIOkHttpClient.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .build();
        SemanticRouter rawRouter = new GraniteSemanticRouter(client, model);
        SemanticRouter router = guarded ? new GuardedSemanticRouter(rawRouter) : rawRouter;
        List<RoutingResult> results = new ArrayList<>();

        for (RoutingCase routingCase : CASES) {
            long startedAt = System.nanoTime();
            try {
                Capability actual = router.classify(routingCase.command());
                results.add(new RoutingResult(routingCase, actual, null, System.nanoTime() - startedAt));
            }
            catch (RuntimeException exception) {
                results.add(new RoutingResult(routingCase, null, exception, System.nanoTime() - startedAt));
            }
        }

        long errors = results.stream().filter(result -> result.error() != null).count();
        long correct = results.stream().filter(RoutingResult::isCorrect).count();
        double accuracy = (double) correct / results.size();
        List<Long> latencies = results.stream().map(RoutingResult::latencyNanos)
                .sorted(Comparator.naturalOrder()).toList();
        double p50 = percentileMillis(latencies, 0.50);
        double p95 = percentileMillis(latencies, 0.95);
        String report = formatReport(model, guarded, results, correct, errors, accuracy, p50, p95);
        System.out.println(report);

        if (Boolean.getBoolean("evaluation.report-only")) {
            return;
        }
        double minimumAccuracy = Double.parseDouble(
                System.getProperty("evaluation.minimum-routing-accuracy", "0.90"));
        assertEquals(0, errors, report);
        assertTrue(accuracy >= minimumAccuracy, report);
    }

    private String formatReport(String model, boolean guarded, List<RoutingResult> results,
                                long correct, long errors, double accuracy, double p50, double p95) {
        StringBuilder report = new StringBuilder(System.lineSeparator())
                .append("Semantic routing corpus evaluation").append(System.lineSeparator())
                .append("model=").append(model).append(" guarded=").append(guarded).append(System.lineSeparator())
                .append(String.format(Locale.ROOT,
                        "cases=%d correct=%d errors=%d accuracy=%.4f p50=%.2fms p95=%.2fms%n",
                        results.size(), correct, errors, accuracy, p50, p95));
        results.stream().filter(result -> !result.isCorrect()).forEach(result -> report
                .append("- ").append(result.routingCase().id())
                .append(" expected=").append(result.routingCase().expected())
                .append(" actual=").append(result.actual())
                .append(result.error() == null ? "" : " error=" + result.error())
                .append(System.lineSeparator()));
        return report.toString();
    }

    private double percentileMillis(List<Long> sortedLatencies, double percentile) {
        int index = Math.max((int) Math.ceil(percentile * sortedLatencies.size()) - 1, 0);
        return sortedLatencies.get(index) / 1_000_000.0;
    }

    private record RoutingCase(String id, String command, Capability expected) {
    }

    private record RoutingResult(RoutingCase routingCase, Capability actual,
                                 RuntimeException error, long latencyNanos) {
        private boolean isCorrect() {
            return error == null && routingCase.expected() == actual;
        }
    }
}

package com.fuad.evaluation.model;

import com.fuad.assistant.skills.general.GeneralBackend;
import com.fuad.assistant.skills.general.GeneralComplexityClassifier;
import com.fuad.assistant.skills.general.LocalGeneralComplexityClassifier;
import com.fuad.config.AppConfig;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("model-evaluation")
class LocalGeneralComplexityClassifierCorpusTest {
    private static final List<Case> CASES = List.of(
            new Case("local-01", "¿Quién fue Alan Turing?", GeneralBackend.QWEN_LOCAL),
            new Case("local-02", "¿Qué es RSA?", GeneralBackend.QWEN_LOCAL),
            new Case("local-03", "Explícame cómo funciona HTTPS", GeneralBackend.QWEN_LOCAL),
            new Case("local-04", "¿Por qué el cielo es azul?", GeneralBackend.QWEN_LOCAL),
            new Case("local-05", "Recomiéndame una película de ciencia ficción", GeneralBackend.QWEN_LOCAL),
            new Case("local-06", "Resume el argumento de Don Quijote", GeneralBackend.QWEN_LOCAL),
            new Case("local-07", "¿Cuál es la diferencia entre RAM y almacenamiento?", GeneralBackend.QWEN_LOCAL),
            new Case("local-08", "Dame tres ideas sencillas para una cena", GeneralBackend.QWEN_LOCAL),
            new Case("local-09", "¿Cómo funciona una brújula?", GeneralBackend.QWEN_LOCAL),
            new Case("local-10", "Explica qué es una API REST", GeneralBackend.QWEN_LOCAL),
            new Case("gpt-01", "Compara RSA-PSS y ECDSA para firmar firmware y justifica la elección",
                    GeneralBackend.GPT),
            new Case("gpt-02", "Diseña una arquitectura tolerante a fallos con tres regiones y consistencia causal",
                    GeneralBackend.GPT),
            new Case("gpt-03", "Demuestra paso a paso por qué este algoritmo tiene complejidad amortizada O(1)",
                    GeneralBackend.GPT),
            new Case("gpt-04", "Analiza cinco estrategias de migración de un monolito y sus riesgos operativos",
                    GeneralBackend.GPT),
            new Case("gpt-05", "Encuentra el error conceptual en esta prueba matemática y propón una corrección",
                    GeneralBackend.GPT),
            new Case("gpt-06", "Diseña un protocolo de consenso para nodos bizantinos con restricciones de latencia",
                    GeneralBackend.GPT),
            new Case("gpt-07", "Compara cuatro esquemas de cifrado poscuántico para un dispositivo embebido",
                    GeneralBackend.GPT),
            new Case("gpt-08", "Propón y evalúa una estrategia completa para depurar una condición de carrera intermitente",
                    GeneralBackend.GPT),
            new Case("gpt-09", "Optimiza este diseño de base de datos considerando consistencia, coste y recuperación",
                    GeneralBackend.GPT),
            new Case("gpt-10", "Razona sobre los tradeoffs de cinco modelos de aislamiento en este sistema distribuido",
                    GeneralBackend.GPT)
    );

    @Test
    void classifierShouldMeetCorpusThreshold() {
        String model = System.getProperty("evaluation.model", AppConfig.LOCAL_MODEL_ID);
        OpenAIClient client = OpenAIOkHttpClient.builder()
                .baseUrl(System.getProperty("evaluation.base-url", AppConfig.LOCAL_AI_BASE_URL))
                .apiKey(System.getProperty("evaluation.api-key", AppConfig.LOCAL_AI_API_KEY))
                .build();
        GeneralComplexityClassifier classifier = new LocalGeneralComplexityClassifier(client, model);
        List<Result> results = new ArrayList<>();
        for (Case testCase : CASES) {
            try {
                results.add(new Result(testCase, classifier.classify(testCase.command()), null));
            }
            catch (RuntimeException error) {
                results.add(new Result(testCase, null, error));
            }
        }

        long errors = results.stream().filter(result -> result.error() != null).count();
        long correct = results.stream().filter(Result::isCorrect).count();
        double accuracy = (double) correct / results.size();
        String report = format(model, results, correct, errors, accuracy);
        System.out.println(report);
        if (!Boolean.getBoolean("evaluation.report-only")) {
            assertEquals(0, errors, report);
            assertTrue(accuracy >= Double.parseDouble(System.getProperty(
                    "evaluation.minimum-general-backend-accuracy", "0.90")), report);
        }
    }

    private String format(String model, List<Result> results, long correct,
                          long errors, double accuracy) {
        StringBuilder out = new StringBuilder("\nGeneral backend corpus evaluation\n")
                .append("model=").append(model).append('\n')
                .append(String.format(Locale.ROOT,
                        "cases=%d correct=%d errors=%d accuracy=%.4f%n",
                        results.size(), correct, errors, accuracy));
        results.stream().filter(result -> !result.isCorrect()).forEach(result -> out
                .append("- ").append(result.testCase().id()).append(" expected=")
                .append(result.testCase().expected()).append(" actual=").append(result.actual())
                .append(result.error() == null ? "" : " error=" + result.error()).append('\n'));
        return out.toString();
    }

    private record Case(String id, String command, GeneralBackend expected) {}

    private record Result(Case testCase, GeneralBackend actual, RuntimeException error) {
        private boolean isCorrect() {
            return error == null && testCase.expected() == actual;
        }
    }
}

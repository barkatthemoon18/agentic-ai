package com.fuad.evaluation.model;

import com.fuad.assistant.skills.general.GeneralBackend;
import com.fuad.assistant.skills.general.GeneralBackendClassification;
import com.fuad.assistant.skills.general.InvalidGeneralBackendOutputException;
import com.fuad.assistant.skills.general.LocalGeneralComplexityClassifier;
import com.fuad.config.AppConfig;
import com.fuad.evaluation.classification.DecisionCorpusEvaluator;
import com.fuad.evaluation.classification.DecisionCorpusLoader;
import com.fuad.evaluation.classification.DecisionEvaluationFailure;
import com.fuad.evaluation.classification.DecisionEvaluationReport;
import com.fuad.evaluation.classification.DecisionOutcome;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("model-evaluation")
class LocalGeneralComplexityClassifierCorpusTest {
    private static final List<String> LABELS = List.of("qwen_local", "gpt");

    @Test
    void classifierShouldMeetCorpusThreshold() {
        String corpus = corpus();
        String model = System.getProperty("evaluation.model", AppConfig.LOCAL_MODEL_ID);
        OpenAIClient client = OpenAIOkHttpClient.builder()
                .baseUrl(System.getProperty("evaluation.base-url", AppConfig.LOCAL_AI_BASE_URL))
                .apiKey(System.getProperty("evaluation.api-key", AppConfig.LOCAL_AI_API_KEY)).build();
        LocalGeneralComplexityClassifier classifier = new LocalGeneralComplexityClassifier(client, model);
        DecisionEvaluationReport report = new DecisionCorpusEvaluator().evaluateDetailed(
                new DecisionCorpusLoader().loadResource(
                        "evaluation/general-backend-" + corpus + ".jsonl", Set.copyOf(LABELS), Set.of()),
                LABELS, testCase -> classify(classifier, testCase.query()));
        String formatted = report.format("General backend corpus evaluation",
                "model=" + model + " corpus=" + corpus);
        System.out.println(formatted);
        if (!Boolean.getBoolean("evaluation.report-only")) {
            assertEquals(0, report.errorCount(), formatted);
            assertTrue(report.macroF1() >= Double.parseDouble(System.getProperty(
                    "evaluation.minimum-general-backend-macro-f1", "0.90")), formatted);
        }
    }

    private String label(GeneralBackend backend) {
        return backend == GeneralBackend.QWEN_LOCAL ? "qwen_local" : "gpt";
    }

    private DecisionOutcome classify(LocalGeneralComplexityClassifier classifier, String query) {
        try {
            GeneralBackendClassification result = classifier.classifyDetailed(query);
            return new DecisionOutcome(label(result.backend()), result.attempts());
        }
        catch (InvalidGeneralBackendOutputException error) {
            throw new DecisionEvaluationFailure(error.getAttempts(), error);
        }
    }

    private String corpus() {
        String value = System.getProperty("evaluation.corpus", "development").trim().toLowerCase();
        if (!Set.of("development", "holdout", "holdout-v1").contains(value)) {
            throw new IllegalArgumentException(
                    "evaluation.corpus must be development, holdout, or holdout-v1");
        }
        return value.equals("holdout") ? "holdout-v2" : value;
    }
}

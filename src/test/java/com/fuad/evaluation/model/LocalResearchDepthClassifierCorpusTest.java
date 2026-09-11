package com.fuad.evaluation.model;

import com.fuad.assistant.skills.research.LocalResearchDepthClassifier;
import com.fuad.config.AppConfig;
import com.fuad.enums.ResearchDepth;
import com.fuad.evaluation.classification.DecisionCorpusEvaluator;
import com.fuad.evaluation.classification.DecisionCorpusLoader;
import com.fuad.evaluation.classification.DecisionEvaluationReport;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("model-evaluation")
class LocalResearchDepthClassifierCorpusTest {
    private static final List<String> LABELS = List.of("quick", "deep");

    @Test
    void classifierShouldMeetCorpusThreshold() {
        String corpus = corpus();
        String model = System.getProperty("evaluation.model", AppConfig.LOCAL_MODEL_ID);
        OpenAIClient client = OpenAIOkHttpClient.builder()
                .baseUrl(System.getProperty("evaluation.base-url", AppConfig.LOCAL_AI_BASE_URL))
                .apiKey(System.getProperty("evaluation.api-key", AppConfig.LOCAL_AI_API_KEY)).build();
        LocalResearchDepthClassifier classifier = new LocalResearchDepthClassifier(client, model);
        DecisionEvaluationReport report = new DecisionCorpusEvaluator().evaluate(
                new DecisionCorpusLoader().loadResource(
                        "evaluation/research-depth-" + corpus + ".jsonl", Set.copyOf(LABELS), Set.of()),
                LABELS, testCase -> classifier.classify(testCase.query()) == ResearchDepth.QUICK
                        ? "quick" : "deep");
        String formatted = report.format("Research depth corpus evaluation",
                "model=" + model + " corpus=" + corpus);
        System.out.println(formatted);
        if (!Boolean.getBoolean("evaluation.report-only")) {
            assertEquals(0, report.errorCount(), formatted);
            assertTrue(report.macroF1() >= Double.parseDouble(System.getProperty(
                    "evaluation.minimum-research-depth-macro-f1", "0.90")), formatted);
        }
    }

    private String corpus() {
        String value = System.getProperty("evaluation.corpus", "development").trim().toLowerCase();
        if (!Set.of("development", "holdout").contains(value)) {
            throw new IllegalArgumentException("evaluation.corpus must be development or holdout");
        }
        return value;
    }
}

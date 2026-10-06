package com.fuad.evaluation.model;

import com.fuad.assistant.skills.research.LocalResearchPlanClassifier;
import com.fuad.assistant.skills.research.ResearchAccess;
import com.fuad.config.AppConfig;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.fuad.evaluation.classification.DecisionCorpusEvaluator;
import com.fuad.evaluation.classification.DecisionCorpusLoader;
import com.fuad.evaluation.classification.DecisionEvaluationReport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("model-evaluation")
class ResearchBackendClassifierCorpusTest {
    private static final List<String> LABELS = List.of("qwen_local", "gpt_web");

    @Test
    void classifierShouldMeetCorpusThreshold() {
        String corpus = corpus();
        LocalResearchPlanClassifier classifier = new LocalResearchPlanClassifier(
                OpenAIOkHttpClient.builder()
                        .baseUrl(System.getProperty("evaluation.base-url", AppConfig.LOCAL_AI_BASE_URL))
                        .apiKey(System.getProperty("evaluation.api-key", AppConfig.LOCAL_AI_API_KEY)).build(),
                System.getProperty("evaluation.model", AppConfig.LOCAL_MODEL_ID));
        DecisionEvaluationReport report = new DecisionCorpusEvaluator().evaluate(
                new DecisionCorpusLoader().loadResource(
                        "evaluation/research-backend-" + corpus + ".jsonl",
                        Set.copyOf(LABELS), Set.copyOf(LABELS)),
                LABELS, testCase -> label(classifier.classify(testCase.query()).access()));
        String formatted = report.format("Research backend corpus evaluation", "corpus=" + corpus + " projection=access inheritedBackend-not-an-input");
        System.out.println(formatted);
        if (!Boolean.getBoolean("evaluation.report-only")) {
            assertEquals(0, report.errorCount(), formatted);
            assertTrue(report.macroF1() >= Double.parseDouble(System.getProperty(
                    "evaluation.minimum-research-backend-macro-f1", "0.90")), formatted);
        }
    }

    private String label(ResearchAccess access) {
        return access == ResearchAccess.MODEL_KNOWLEDGE ? "qwen_local" : "gpt_web";
    }

    private String corpus() {
        String value = System.getProperty("evaluation.corpus", "development").trim().toLowerCase();
        if (!Set.of("development", "holdout").contains(value)) {
            throw new IllegalArgumentException("evaluation.corpus must be development or holdout");
        }
        return value;
    }
}

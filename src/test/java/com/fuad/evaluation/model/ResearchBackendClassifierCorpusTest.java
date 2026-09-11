package com.fuad.evaluation.model;

import com.fuad.assistant.skills.research.DefaultResearchBackendClassifier;
import com.fuad.assistant.skills.research.ResearchBackend;
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
        DefaultResearchBackendClassifier classifier = new DefaultResearchBackendClassifier();
        DecisionEvaluationReport report = new DecisionCorpusEvaluator().evaluate(
                new DecisionCorpusLoader().loadResource(
                        "evaluation/research-backend-" + corpus + ".jsonl",
                        Set.copyOf(LABELS), Set.copyOf(LABELS)),
                LABELS, testCase -> label(classifier.classify(testCase.query(),
                        inherited(testCase.normalizedInheritedBackend()))));
        String formatted = report.format("Research backend corpus evaluation", "corpus=" + corpus);
        System.out.println(formatted);
        if (!Boolean.getBoolean("evaluation.report-only")) {
            assertEquals(0, report.errorCount(), formatted);
            assertTrue(report.macroF1() >= Double.parseDouble(System.getProperty(
                    "evaluation.minimum-research-backend-macro-f1", "0.90")), formatted);
        }
    }

    private ResearchBackend inherited(String label) {
        if (label == null) {
            return null;
        }
        return label.equals("qwen_local") ? ResearchBackend.QWEN_LOCAL : ResearchBackend.GPT_WEB;
    }

    private String label(ResearchBackend backend) {
        return backend == ResearchBackend.QWEN_LOCAL ? "qwen_local" : "gpt_web";
    }

    private String corpus() {
        String value = System.getProperty("evaluation.corpus", "development").trim().toLowerCase();
        if (!Set.of("development", "holdout").contains(value)) {
            throw new IllegalArgumentException("evaluation.corpus must be development or holdout");
        }
        return value;
    }
}

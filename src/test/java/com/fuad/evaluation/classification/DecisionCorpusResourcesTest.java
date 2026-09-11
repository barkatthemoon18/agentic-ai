package com.fuad.evaluation.classification;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DecisionCorpusResourcesTest {
    private final DecisionCorpusLoader loader = new DecisionCorpusLoader();

    @ParameterizedTest(name = "{0}")
    @MethodSource("corpora")
    void corpusShouldHaveTheExpectedSizeAndBalance(String resource, Set<String> labels,
                                                   Set<String> inheritedLabels, int supportPerLabel) {
        List<DecisionCorpusCase> cases = loader.loadResource(resource, labels, inheritedLabels);

        assertEquals(labels.size() * supportPerLabel, cases.size());
        for (String label : labels) {
            assertEquals(supportPerLabel, cases.stream()
                    .filter(testCase -> label.equals(testCase.normalizedExpected())).count());
        }
    }

    private static Stream<Arguments> corpora() {
        Set<String> general = Set.of("qwen_local", "gpt");
        Set<String> researchBackend = Set.of("qwen_local", "gpt_web");
        Set<String> depth = Set.of("quick", "deep");
        return Stream.of(
                Arguments.of("evaluation/general-backend-development.jsonl", general, Set.of(), 15),
                Arguments.of("evaluation/general-backend-holdout-v1.jsonl", general, Set.of(), 10),
                Arguments.of("evaluation/general-backend-holdout-v2.jsonl", general, Set.of(), 10),
                Arguments.of("evaluation/research-backend-development.jsonl",
                        researchBackend, researchBackend, 15),
                Arguments.of("evaluation/research-backend-holdout.jsonl",
                        researchBackend, researchBackend, 10),
                Arguments.of("evaluation/research-depth-development.jsonl", depth, Set.of(), 15),
                Arguments.of("evaluation/research-depth-holdout.jsonl", depth, Set.of(), 10));
    }
}

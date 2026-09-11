package com.fuad.evaluation.classification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

public final class DecisionCorpusEvaluator {
    public DecisionEvaluationReport evaluate(List<DecisionCorpusCase> cases,
                                             List<String> labels,
                                             Function<DecisionCorpusCase, String> classifier) {
        return evaluateDetailed(cases, labels,
                testCase -> new DecisionOutcome(classifier.apply(testCase), 1));
    }

    public DecisionEvaluationReport evaluateDetailed(List<DecisionCorpusCase> cases,
                                                     List<String> labels,
                                                     Function<DecisionCorpusCase, DecisionOutcome> classifier) {
        Objects.requireNonNull(cases, "cases cannot be null");
        Objects.requireNonNull(classifier, "classifier cannot be null");
        List<DecisionEvaluationResult> results = new ArrayList<>();
        for (DecisionCorpusCase testCase : cases) {
            long start = System.nanoTime();
            try {
                DecisionOutcome outcome = classifier.apply(testCase);
                String actual = outcome.label();
                results.add(new DecisionEvaluationResult(testCase,
                        actual == null ? null : actual.trim().toLowerCase(Locale.ROOT), null,
                        System.nanoTime() - start, outcome.attempts()));
            }
            catch (RuntimeException error) {
                int attempts = error instanceof DecisionEvaluationFailure failure
                        ? failure.attempts() : 1;
                RuntimeException cause = error instanceof DecisionEvaluationFailure failure
                        && failure.getCause() instanceof RuntimeException runtimeCause
                        ? runtimeCause : error;
                results.add(new DecisionEvaluationResult(testCase, null, cause,
                        System.nanoTime() - start, attempts));
            }
        }
        return new DecisionEvaluationReport(labels, results);
    }
}

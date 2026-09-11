package com.fuad.evaluation.classification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class DecisionCorpusLoader {
    private final ObjectMapper objectMapper;

    public DecisionCorpusLoader() {
        this(new ObjectMapper());
    }

    DecisionCorpusLoader(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper cannot be null");
    }

    public List<DecisionCorpusCase> loadResource(String resourcePath, Set<String> expectedLabels,
                                                 Set<String> inheritedLabels) {
        String path = requireText(resourcePath, "resourcePath");
        InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
        if (input == null) {
            throw new IllegalArgumentException("Corpus resource not found: " + path);
        }
        try (Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            return load(reader, path, expectedLabels, inheritedLabels);
        }
        catch (IOException error) {
            throw new IllegalStateException("Could not close corpus resource: " + path, error);
        }
    }

    List<DecisionCorpusCase> load(Reader reader, String sourceName, Set<String> expectedLabels,
                                  Set<String> inheritedLabels) {
        Objects.requireNonNull(reader, "reader cannot be null");
        String source = requireText(sourceName, "sourceName");
        Set<String> expected = normalizedLabels(expectedLabels, "expectedLabels");
        Set<String> inherited = normalizedLabels(inheritedLabels, "inheritedLabels");
        List<DecisionCorpusCase> cases = new ArrayList<>();
        Set<String> identifiers = new HashSet<>();
        try (BufferedReader buffered = new BufferedReader(reader)) {
            String line;
            int lineNumber = 0;
            while ((line = buffered.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                DecisionCorpusCase testCase = parse(line, source, lineNumber);
                validate(testCase, source, lineNumber, expected, inherited);
                if (!identifiers.add(testCase.id())) {
                    throw invalid(source, lineNumber, "duplicate id '" + testCase.id() + "'");
                }
                cases.add(testCase);
            }
        }
        catch (IOException error) {
            throw new IllegalStateException("Could not read corpus " + source, error);
        }
        if (cases.isEmpty()) {
            throw new IllegalArgumentException("Corpus contains no cases: " + source);
        }
        return List.copyOf(cases);
    }

    private DecisionCorpusCase parse(String line, String source, int lineNumber) {
        try {
            return objectMapper.readValue(line, DecisionCorpusCase.class);
        }
        catch (JsonProcessingException error) {
            throw invalid(source, lineNumber, "invalid JSON: " + error.getOriginalMessage());
        }
    }

    private void validate(DecisionCorpusCase testCase, String source, int lineNumber,
                          Set<String> expectedLabels, Set<String> inheritedLabels) {
        requireField(testCase.id(), "id", source, lineNumber);
        requireField(testCase.query(), "query", source, lineNumber);
        requireField(testCase.expected(), "expected", source, lineNumber);
        requireField(testCase.rationale(), "rationale", source, lineNumber);
        if (testCase.tags() == null) {
            throw invalid(source, lineNumber, "tags cannot be null");
        }
        if (testCase.tags().stream().anyMatch(tag -> tag == null || tag.isBlank())) {
            throw invalid(source, lineNumber, "tags cannot contain blank values");
        }
        if (!expectedLabels.contains(testCase.normalizedExpected())) {
            throw invalid(source, lineNumber, "unknown expected label '" + testCase.expected() + "'");
        }
        String inherited = testCase.normalizedInheritedBackend();
        if (inherited != null && !inheritedLabels.contains(inherited)) {
            throw invalid(source, lineNumber, "unknown inherited backend '"
                    + testCase.inheritedBackend() + "'");
        }
    }

    private Set<String> normalizedLabels(Set<String> labels, String field) {
        Objects.requireNonNull(labels, field + " cannot be null");
        return labels.stream().map(label -> requireText(label, field).toLowerCase(java.util.Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private void requireField(String value, String field, String source, int lineNumber) {
        if (value == null || value.isBlank()) {
            throw invalid(source, lineNumber, field + " is required");
        }
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return value.trim();
    }

    private IllegalArgumentException invalid(String source, int lineNumber, String reason) {
        return new IllegalArgumentException(source + ":" + lineNumber + " - " + reason);
    }
}

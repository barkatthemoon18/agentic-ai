package com.fuad.evaluation.utterance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuad.enums.Capability;
import com.fuad.enums.UtteranceDecision;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public class UtteranceCorpusLoader {
    private final ObjectMapper objectMapper;

    public UtteranceCorpusLoader() {
        this(new ObjectMapper());
    }

    UtteranceCorpusLoader(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper cannot be null");
    }

    public List<UtteranceEvaluationCase> loadResource(String resourcePath) {
        String normalizedPath = requireText(resourcePath, "resourcePath");
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        InputStream input = classLoader.getResourceAsStream(normalizedPath);
        if (input == null) {
            throw new IllegalArgumentException("Corpus resource not found: " + normalizedPath);
        }
        try (Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            return load(reader, normalizedPath);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not close corpus resource: " + normalizedPath, exception);
        }
    }

    List<UtteranceEvaluationCase> load(Reader reader, String sourceName) {
        Objects.requireNonNull(reader, "reader cannot be null");
        String source = requireText(sourceName, "sourceName");
        List<UtteranceEvaluationCase> cases = new ArrayList<>();
        Set<String> identifiers = new HashSet<>();
        try (BufferedReader bufferedReader = new BufferedReader(reader)) {
            String line;
            int lineNumber = 0;
            while ((line = bufferedReader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                UtteranceEvaluationCase evaluationCase = parse(line, source, lineNumber);
                validate(evaluationCase, source, lineNumber);
                if (!identifiers.add(evaluationCase.getId())) {
                    throw invalid(source, lineNumber, "duplicate id '" + evaluationCase.getId() + "'");
                }
                cases.add(evaluationCase);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read corpus " + source, exception);
        }
        if (cases.isEmpty()) {
            throw new IllegalArgumentException("Corpus contains no cases: " + source);
        }
        return List.copyOf(cases);
    }

    private UtteranceEvaluationCase parse(String line, String source, int lineNumber) {
        try {
            return objectMapper.readValue(line, UtteranceEvaluationCase.class);
        } catch (JsonProcessingException exception) {
            throw invalid(source, lineNumber, "invalid JSON: " + exception.getOriginalMessage());
        }
    }

    private void validate(UtteranceEvaluationCase evaluationCase, String source, int lineNumber) {
        requireField(evaluationCase.getId(), "id", source, lineNumber);
        requireField(evaluationCase.getCurrentText(), "currentText", source, lineNumber);
        requireField(evaluationCase.getExpected(), "expected", source, lineNumber);
        requireField(evaluationCase.getRationale(), "rationale", source, lineNumber);
        if (evaluationCase.getContextAvailable() == null) {
            throw invalid(source, lineNumber, "contextAvailable is required");
        }
        validateExpected(evaluationCase, source, lineNumber);
        validateTags(evaluationCase, source, lineNumber);
        if (evaluationCase.getContextAvailable()) {
            requireField(evaluationCase.getPreviousUserText(), "previousUserText", source, lineNumber);
            requireField(evaluationCase.getPreviousAssistantText(), "previousAssistantText", source, lineNumber);
            requireField(evaluationCase.getOwner(), "owner", source, lineNumber);
            validateOwner(evaluationCase.getOwner(), source, lineNumber);
        } else {
            rejectUnexpectedContext(evaluationCase, source, lineNumber);
        }
    }

    private void validateExpected(UtteranceEvaluationCase evaluationCase, String source, int lineNumber) {
        UtteranceDecision expected;
        try {
            expected = evaluationCase.expectedDecision();
        } catch (IllegalArgumentException exception) {
            throw invalid(source, lineNumber, "unknown expected label '" + evaluationCase.getExpected() + "'");
        }
        if (expected == UtteranceDecision.FOLLOW_UP && !evaluationCase.getContextAvailable()) {
            throw invalid(source, lineNumber, "FOLLOW_UP requires contextAvailable=true");
        }
    }

    private void validateTags(UtteranceEvaluationCase evaluationCase, String source, int lineNumber) {
        if (evaluationCase.getTags() == null) {
            throw invalid(source, lineNumber, "tags cannot be null");
        }
        for (String tag : evaluationCase.getTags()) {
            if (tag == null || tag.isBlank()) {
                throw invalid(source, lineNumber, "tags cannot contain blank values");
            }
        }
    }

    private void validateOwner(String owner, String source, int lineNumber) {
        try {
            Capability.fromValue(owner);
        } catch (IllegalArgumentException firstException) {
            try {
                Capability.valueOf(owner.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException secondException) {
                throw invalid(source, lineNumber, "unknown owner '" + owner + "'");
            }
        }
    }

    private void rejectUnexpectedContext(UtteranceEvaluationCase evaluationCase, String source, int lineNumber) {
        if (hasText(evaluationCase.getPreviousUserText()) || hasText(evaluationCase.getPreviousAssistantText())
                || hasText(evaluationCase.getOwner())) {
            throw invalid(source, lineNumber, "context fields require contextAvailable=true");
        }
    }

    private void requireField(String value, String field, String source, int lineNumber) {
        if (!hasText(value)) {
            throw invalid(source, lineNumber, field + " is required");
        }
    }

    private String requireText(String value, String field) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private IllegalArgumentException invalid(String source, int lineNumber, String reason) {
        return new IllegalArgumentException(source + ":" + lineNumber + " - " + reason);
    }
}

package com.fuad.activation.wake;

import com.fuad.enums.WakeMatchStatus;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WakeWordMatcher {
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\p{L}\\p{N}]+");
    private final List<String> wakeWords;
    private final double highThreshold;
    private final double lowThreshold;

    public WakeWordMatcher(List<String> wakeWords, double highThreshold, double lowThreshold) {
        this.wakeWords = wakeWords;
        if (lowThreshold >= highThreshold) {
            throw new IllegalArgumentException("Low threshold must be lower than high threshold");
        }
        this.highThreshold = highThreshold;
        this.lowThreshold = lowThreshold;
    }

    public WakeWordMatch match(String text) {
        if (text == null || text.isBlank()) {
            return WakeWordMatch.none();
        }
        List<Token> tokens = tokenize(text);
        if (tokens.isEmpty()) {
            return WakeWordMatch.none();
        }
        CandidateMatch bestMatch = null;
        for (String wakeWord : wakeWords) {
            String normalizedWake = normalize(wakeWord);
            int wakeTokenCount = tokenize(wakeWord).size();
            int minTokens = Math.max(1, wakeTokenCount - 1);
            int maxTokens = Math.min(tokens.size(), wakeTokenCount + 1);
            for (int tokenCount = minTokens; tokenCount <= maxTokens; tokenCount++) {
                String candidate = buildCandidate(text, tokens, tokenCount);
                double similarity = similarity(normalize(candidate), normalizedWake);
                if (bestMatch == null || similarity > bestMatch.getSimilarity()) {
                    bestMatch = new CandidateMatch(candidate, tokenCount, similarity);
                }
            }
        }
        if (bestMatch == null || bestMatch.getSimilarity() < lowThreshold) {
            return WakeWordMatch.none();
        }
        String command = extractCommand(text, tokens, bestMatch.getTokenCount());
        WakeMatchStatus status = bestMatch.getSimilarity() >= highThreshold ? WakeMatchStatus.MATCH : WakeMatchStatus.AMBIGUOUS;
        return new WakeWordMatch(status, bestMatch.getCandidate(), command,  bestMatch.getSimilarity());
    }

    private List<Token> tokenize(String text) {
        List<Token> tokens = new ArrayList<>();
        Matcher matcher = TOKEN_PATTERN.matcher(text);
        while (matcher.find()) {
            tokens.add(new Token(matcher.group(), matcher.start(), matcher.end()));
        }
        return tokens;
    }

    private String buildCandidate(String text, List<Token> tokens,  int tokenCount) {
        Token first = tokens.getFirst();
        Token last = tokens.get(tokenCount - 1);
        return text.substring(first.start, last.end);
    }

    private String extractCommand(String text, List<Token> tokens, int tokenCount) {
        Token last =  tokens.get(tokenCount - 1);
        return text.substring(last.getEnd()).replaceFirst("^[,.:;!?¿¡\\s]+", "").trim();
    }

    private String normalize(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}\\s]", "").replaceAll("\\s+", " ").trim();
    }

    private double similarity(String candidate, String normalizedWake) {
        if (candidate.equals(normalizedWake)) {
            return 1.0;
        }
        if (candidate.isEmpty() || normalizedWake.isEmpty()) {
            return 0.0;
        }
        int distance = levenshteinDistance(candidate, normalizedWake);
        int maxLength = Math.max(candidate.length(), normalizedWake.length());
        return 1.0 - ((double) distance / maxLength);
    }

    private int levenshteinDistance(String candidate, String normalizedWake) {
        int[] previous = new int[normalizedWake.length() + 1];
        int[] current = new int[normalizedWake.length() + 1];
        for (int j = 0; j <= normalizedWake.length(); j++) {
            previous[j] = j;
        }
        for (int i =  1; i <= candidate.length(); i++) {
            current[0] = i;
            for (int j = 1 ; j <= normalizedWake.length(); j++) {
                int substitutionCost = candidate.charAt(i - 1) == normalizedWake.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + substitutionCost);
            }
            int[] temp = previous;
            previous = current;
            current = temp;
        }
        return previous[normalizedWake.length()];
    }
}

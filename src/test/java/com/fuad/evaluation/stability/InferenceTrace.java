package com.fuad.evaluation.stability;

import com.openai.client.OpenAIClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Records actual SDK requests and responses without changing production classifiers. */
final class InferenceTrace {
    final OpenAIClient client = mock(OpenAIClient.class, RETURNS_DEEP_STUBS);
    private final List<Map<String, Object>> attempts = new ArrayList<>();

    InferenceTrace(OpenAIClient delegate) {
        when(client.chat().completions().create(any(ChatCompletionCreateParams.class)))
                .thenAnswer(invocation -> record(delegate, invocation.getArgument(0)));
    }

    void reset() { attempts.clear(); }
    List<Map<String, Object>> snapshot() { return List.copyOf(attempts); }

    private ChatCompletion record(OpenAIClient delegate, ChatCompletionCreateParams params) {
        Map<String, Object> attempt = new LinkedHashMap<>();
        attempt.put("number", attempts.size() + 1);
        attempt.put("model", params.model().toString());
        attempt.put("temperature", params.temperature().orElse(null));
        attempt.put("maxCompletionTokens", params.maxCompletionTokens().orElse(null));
        attempt.put("messages", params.messages().stream().map(message -> {
            String role = message.isSystem() ? "system" : message.isUser() ? "user" : "assistant";
            Object value = message.isSystem() ? message.asSystem()
                    : message.isUser() ? message.asUser() : message.asAssistant();
            return Map.of("role", role, "content", content(value));
        }).toList());
        attempts.add(attempt);
        long start = System.nanoTime();
        try {
            ChatCompletion completion = delegate.chat().completions().create(params);
            attempt.put("output", completion.choices().isEmpty() ? null
                    : completion.choices().getFirst().message().content().orElse(null));
            attempt.put("completionId", completion.id());
            attempt.put("promptTokens", completion.usage().map(usage -> usage.promptTokens()).orElse(null));
            attempt.put("finishReason", completion.choices().isEmpty() ? null
                    : completion.choices().getFirst().finishReason().toString());
            attempt.put("error", null);
            return completion;
        } catch (RuntimeException error) {
            attempt.put("output", null);
            attempt.put("error", error.toString());
            throw error;
        } finally {
            attempt.put("latencyMillis", (System.nanoTime() - start) / 1_000_000.0);
        }
    }

    private static String content(Object message) {
        try {
            Object value = message.getClass().getMethod("content").invoke(message);
            if (value instanceof Optional<?> optional) value = optional.orElse(null);
            return value == null ? "" : (String) value.getClass().getMethod("asText").invoke(value);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Unable to capture text message", error);
        }
    }
}

package com.fuad.assistant.local;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class LocalQwenChatClient {
    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(2);
    private static final int ERROR_BODY_LIMIT = 500;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI chatEndpoint;
    private final String apiKey;
    private final String model;

    public LocalQwenChatClient(String baseUrl, String apiKey, String model) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
                new ObjectMapper(), baseUrl, apiKey, model);
    }

    LocalQwenChatClient(HttpClient httpClient, ObjectMapper objectMapper,
                        String baseUrl, String apiKey, String model) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient cannot be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper cannot be null");
        this.chatEndpoint = chatEndpoint(baseUrl);
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = requireText(model, "model");
    }

    public String chat(String systemPrompt, List<Message> messages, int maxOutputTokens) {
        String normalizedPrompt = requireText(systemPrompt, "systemPrompt");
        List<Message> normalizedMessages = List.copyOf(Objects.requireNonNull(messages,
                "messages cannot be null"));
        if (normalizedMessages.isEmpty()) {
            throw new IllegalArgumentException("messages cannot be empty");
        }
        if (maxOutputTokens <= 0) {
            throw new IllegalArgumentException("maxOutputTokens must be positive");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("system_prompt", normalizedPrompt);
        payload.put("input", formatMessages(normalizedMessages));
        payload.put("temperature", 0.2);
        payload.put("max_output_tokens", maxOutputTokens);
        payload.put("reasoning", "off");
        payload.put("store", false);

        HttpRequest.Builder request = HttpRequest.newBuilder(chatEndpoint)
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(writePayload(payload)));
        if (!apiKey.isEmpty()) {
            request.header("Authorization", "Bearer " + apiKey);
        }

        try {
            HttpResponse<String> response = httpClient.send(
                    request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw responseError(response.statusCode(), response.body());
            }
            return readAssistantText(response.body());
        }
        catch (HttpTimeoutException e) {
            throw new LocalQwenException(LocalQwenException.Kind.UNAVAILABLE,
                    "Local Qwen request timed out", e);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LocalQwenException(LocalQwenException.Kind.FAILURE,
                    "Local Qwen request was interrupted", e);
        }
        catch (IOException e) {
            throw new LocalQwenException(LocalQwenException.Kind.UNAVAILABLE,
                    "Local Qwen is unavailable", e);
        }
    }

    private String writePayload(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        }
        catch (JsonProcessingException e) {
            throw new LocalQwenException(LocalQwenException.Kind.FAILURE,
                    "Could not serialize local Qwen request", e);
        }
    }

    private String readAssistantText(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root == null || !root.isObject()) {
                throw failure("Local Qwen returned no JSON object");
            }
            JsonNode output = root.path("output");
            if (!output.isArray()) {
                throw failure("Local Qwen returned no output array");
            }
            List<String> messageContents = new ArrayList<>();
            List<String> outputTypes = new ArrayList<>();
            for (JsonNode item : output) {
                outputTypes.add(item.path("type").asText("unknown"));
                JsonNode content = item.path("content");
                if ("message".equals(item.path("type").asText()) && content.isTextual()
                        && !content.asText().isBlank()) {
                    messageContents.add(content.asText().trim());
                }
            }
            String text = String.join("\n", messageContents).trim();
            if (text.isEmpty()) {
                throw failure("Local Qwen returned no assistant text; output types: "
                        + String.join(", ", outputTypes));
            }
            return text;
        }
        catch (JsonProcessingException e) {
            throw new LocalQwenException(LocalQwenException.Kind.FAILURE,
                    "Local Qwen returned invalid JSON", e);
        }
    }

    private LocalQwenException responseError(int status, String body) {
        String summary = summarize(body);
        LocalQwenException.Kind kind = status == 502 || status == 503 || status == 504
                || modelUnavailable(summary)
                ? LocalQwenException.Kind.UNAVAILABLE
                : LocalQwenException.Kind.FAILURE;
        return new LocalQwenException(kind,
                "Local Qwen request failed with HTTP " + status + ": " + summary);
    }

    private boolean modelUnavailable(String body) {
        String normalized = body.toLowerCase(Locale.ROOT);
        return normalized.contains("model not found")
                || normalized.contains("model is not loaded")
                || normalized.contains("model not loaded")
                || normalized.contains("model unavailable")
                || normalized.contains("no model loaded");
    }

    private LocalQwenException failure(String message) {
        return new LocalQwenException(LocalQwenException.Kind.FAILURE, message);
    }

    private static String formatMessages(List<Message> messages) {
        if (messages.size() == 1 && messages.getFirst().role() == Role.USER) {
            return messages.getFirst().content();
        }
        StringBuilder transcript = new StringBuilder("Historial de la conversacion:\n");
        for (Message message : messages) {
            transcript.append(message.role() == Role.USER ? "\nUsuario:\n" : "\nAsistente:\n")
                    .append(message.content()).append('\n');
        }
        transcript.append("\nResponde al ultimo mensaje del usuario.");
        return transcript.toString();
    }

    private static URI chatEndpoint(String baseUrl) {
        String normalized = requireText(baseUrl, "baseUrl");
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        URI endpoint = URI.create(normalized + "/api/v1/chat");
        if (!"http".equalsIgnoreCase(endpoint.getScheme())
                && !"https".equalsIgnoreCase(endpoint.getScheme())) {
            throw new IllegalArgumentException("baseUrl must use http or https");
        }
        return endpoint;
    }

    private static String requireText(String value, String name) {
        String normalized = Objects.requireNonNull(value, name + " cannot be null").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " cannot be empty");
        }
        return normalized;
    }

    private static String summarize(String body) {
        String normalized = body == null ? "" : body.replaceAll("\\s+", " ").trim();
        return normalized.length() <= ERROR_BODY_LIMIT
                ? normalized
                : normalized.substring(0, ERROR_BODY_LIMIT) + "...";
    }

    public record Message(Role role, String content) {
        public Message {
            Objects.requireNonNull(role, "role cannot be null");
            content = requireText(content, "content");
        }
    }

    public enum Role {
        USER,
        ASSISTANT
    }
}

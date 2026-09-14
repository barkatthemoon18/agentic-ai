package com.fuad.assistant.local;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalQwenChatClientTest {
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;
    private int responseStatus;
    private String responseBody;

    @BeforeEach
    void startServer() throws Exception {
        responseStatus = 200;
        responseBody = """
                {
                  "output": [
                    {"type": "reasoning", "content": "internal reasoning"},
                    {"type": "message", "content": "Primera parte."},
                    {"type": "message", "content": "Segunda parte."}
                  ]
                }
                """;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/chat", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatus, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void shouldUseDirectLmStudioChatAndReturnOnlyAssistantMessages() throws Exception {
        LocalQwenChatClient client = client();

        String result = client.chat("Responde en espanol", List.of(
                new LocalQwenChatClient.Message(LocalQwenChatClient.Role.USER, "Pregunta anterior"),
                new LocalQwenChatClient.Message(LocalQwenChatClient.Role.ASSISTANT, "Respuesta anterior"),
                new LocalQwenChatClient.Message(LocalQwenChatClient.Role.USER, "Pregunta actual")), 500);

        assertEquals("Primera parte.\nSegunda parte.", result);
        assertEquals("Bearer test-token", authorization.get());
        JsonNode payload = objectMapper.readTree(requestBody.get());
        assertEquals("qwen-main", payload.get("model").asText());
        assertEquals("Responde en espanol", payload.get("system_prompt").asText());
        assertEquals(0.2, payload.get("temperature").asDouble());
        assertEquals(500, payload.get("max_output_tokens").asInt());
        assertEquals("off", payload.get("reasoning").asText());
        assertFalse(payload.get("store").asBoolean());
        assertTrue(payload.get("input").asText().contains("Usuario:\nPregunta anterior"));
        assertTrue(payload.get("input").asText().contains("Asistente:\nRespuesta anterior"));
        assertTrue(payload.get("input").asText().contains("Usuario:\nPregunta actual"));
    }

    @Test
    void shouldRejectResponseWithoutAssistantMessage() {
        responseBody = """
                {"output":[{"type":"reasoning","content":"unfinished"}]}
                """;

        LocalQwenException exception = assertThrows(LocalQwenException.class,
                () -> client().chat("prompt", List.of(userMessage()), 100));

        assertTrue(exception.getMessage().contains("no assistant text"));
        assertTrue(exception.getMessage().contains("reasoning"));
        assertEquals(LocalQwenException.Kind.FAILURE, exception.getKind());
    }

    @Test
    void shouldReportInvalidJson() {
        responseBody = "not-json";

        LocalQwenException exception = assertThrows(LocalQwenException.class,
                () -> client().chat("prompt", List.of(userMessage()), 100));

        assertTrue(exception.getMessage().contains("invalid JSON"));
        assertEquals(LocalQwenException.Kind.FAILURE, exception.getKind());
    }

    @Test
    void shouldReportNonSuccessfulHttpStatus() {
        responseStatus = 503;
        responseBody = "{\"error\":\"model unavailable\"}";

        LocalQwenException exception = assertThrows(LocalQwenException.class,
                () -> client().chat("prompt", List.of(userMessage()), 100));

        assertTrue(exception.getMessage().contains("HTTP 503"));
        assertTrue(exception.getMessage().contains("model unavailable"));
        assertEquals(LocalQwenException.Kind.UNAVAILABLE, exception.getKind());
    }

    @Test
    void shouldRejectRequestBeforeHttpWhenRuntimeSnapshotIsUnavailable() {
        LocalQwenChatClient client = new LocalQwenChatClient(
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "test-token", "qwen-main", () -> false);

        LocalQwenException exception = assertThrows(LocalQwenException.class,
                () -> client.chat("prompt", List.of(userMessage()), 100));

        assertEquals(LocalQwenException.Kind.UNAVAILABLE, exception.getKind());
        assertEquals(null, requestBody.get());
    }

    @Test
    void shouldDistinguishAnUnavailableModelFromAProviderFailure() {
        responseStatus = 400;
        responseBody = "{\"error\":\"model is not loaded\"}";

        LocalQwenException unavailable = assertThrows(LocalQwenException.class,
                () -> client().chat("prompt", List.of(userMessage()), 100));

        assertEquals(LocalQwenException.Kind.UNAVAILABLE, unavailable.getKind());

        responseStatus = 500;
        responseBody = "{\"error\":\"internal invariant failed\"}";
        LocalQwenException failure = assertThrows(LocalQwenException.class,
                () -> client().chat("prompt", List.of(userMessage()), 100));
        assertEquals(LocalQwenException.Kind.FAILURE, failure.getKind());
    }

    private LocalQwenChatClient client() {
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
        return new LocalQwenChatClient(HttpClient.newHttpClient(), objectMapper,
                baseUrl, "test-token", "qwen-main");
    }

    private LocalQwenChatClient.Message userMessage() {
        return new LocalQwenChatClient.Message(LocalQwenChatClient.Role.USER, "Pregunta");
    }
}

package com.fuad.activation.utterance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuad.enums.UtteranceDecision;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LocalUtteranceClassifierTest {
    @Test
    void shouldBypassRulesOnlyWhenRequestedAndKeepContextProtection() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        List<String> requests = Collections.synchronizedList(new ArrayList<>());
        server.createContext("/v1/chat/completions", exchange -> {
            requests.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = """
                    {"id":"test","object":"chat.completion","created":1,"model":"test-model",
                     "choices":[{"index":0,"message":{"role":"assistant","content":"follow_up"},"finish_reason":"stop"}]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        var client = OpenAIOkHttpClient.builder().baseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1")
                .apiKey("test").maxRetries(0).build();
        try {
            var request = UtteranceClassificationRequest.withoutContext("Explícame RSA");
            assertEquals(UtteranceDecision.NEW_REQUEST, new LocalUtteranceClassifier(client, "test-model").classify(request));
            assertTrue(requests.isEmpty());
            assertEquals(UtteranceDecision.OTHER, new LocalUtteranceClassifier(client, "test-model", false).classify(request));
            var ambient = UtteranceClassificationRequest.withoutContext("La taza está junto al cuaderno.");
            new LocalUtteranceClassifier(client, "test-model", true).classify(ambient);
            assertEquals(2, requests.size());
            var mapper = new ObjectMapper();
            var pure = mapper.readTree(requests.get(0));
            var hybrid = mapper.readTree(requests.get(1));
            assertEquals(pure.get("messages").get(0), hybrid.get("messages").get(0));
            assertEquals(0.0, pure.get("temperature").asDouble());
            assertEquals(8, pure.get("max_completion_tokens").asInt());
        }
        finally {
            client.close();
            server.stop(0);
        }
    }
}

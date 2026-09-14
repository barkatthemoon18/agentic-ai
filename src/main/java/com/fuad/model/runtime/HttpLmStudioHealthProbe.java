package com.fuad.model.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

public final class HttpLmStudioHealthProbe implements ServerHealthProbe {
    private final HttpClient client;
    private final ObjectMapper objectMapper;
    private final URI endpoint;
    private final String apiKey;

    public HttpLmStudioHealthProbe(URI endpoint, String apiKey) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build(),
                new ObjectMapper(), endpoint, apiKey);
    }

    HttpLmStudioHealthProbe(HttpClient client, ObjectMapper objectMapper, URI endpoint, String apiKey) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.endpoint = endpoint;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    @Override
    public Result check() {
        HttpRequest.Builder request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(5)).GET();
        if (!apiKey.isEmpty()) {
            request.header("Authorization", "Bearer " + apiKey);
        }
        try {
            HttpResponse<String> response = client.send(request.build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return new Result(true, false, "HTTP " + response.statusCode());
            }
            JsonNode root = objectMapper.readTree(response.body());
            boolean compatible = root != null && root.isObject()
                    && (root.path("models").isArray() || root.path("data").isArray());
            return compatible
                    ? new Result(true, true, "LM Studio API is healthy")
                    : new Result(true, false, "Port 1234 returned incompatible JSON");
        }
        catch (HttpConnectTimeoutException | ConnectException e) {
            return new Result(false, false, "Nothing is listening on port 1234");
        }
        catch (HttpTimeoutException e) {
            return new Result(true, false, "El puerto acepta conexiones pero el health check expiró");
        }
        catch (Exception e) {
            return new Result(false, false, e.getMessage());
        }
    }
}

package com.zhu.scope.boot.knowledge.embed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/** DashScope 兼容模式的文本向量。只在配置了密钥时创建。 */
public final class DashScopeEmbedder implements BootEmbedder {

    static final URI ENDPOINT =
            URI.create("https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings");

    private static final ObjectMapper JSON = new ObjectMapper();

    private final String apiKey;

    private final String model;

    private final HttpClient client;

    public DashScopeEmbedder(String apiKey, String model) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("embedding api key required");
        }
        this.apiKey = apiKey.trim();
        this.model = model == null || model.isBlank() ? "text-embedding-v4" : model.trim();
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    @Override
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            return new float[0];
        }
        try {
            Map<String, String> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("input", text);
            HttpRequest request = HttpRequest.newBuilder(ENDPOINT)
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("embedding http " + response.statusCode());
            }
            JsonNode vector = JSON.readTree(response.body()).path("data").path(0).path("embedding");
            if (!vector.isArray() || vector.isEmpty()) {
                throw new IllegalStateException("embedding response empty");
            }
            float[] out = new float[vector.size()];
            for (int i = 0; i < vector.size(); i++) {
                out[i] = (float) vector.get(i).asDouble();
            }
            return out;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("embedding interrupted", ex);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("embedding failed", ex);
        }
    }
}

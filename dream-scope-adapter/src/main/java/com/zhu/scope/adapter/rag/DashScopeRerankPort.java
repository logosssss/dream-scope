package com.zhu.scope.adapter.rag;

import com.zhu.scope.knowledge.RerankPort;
import com.zhu.scope.knowledge.RetrieveHit;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DashScope 文本精排（gte-rerank-v2 / qwen3-rerank 等）。失败时由 {@code AdvancedRetrievePort} 退回原候选。
 */
public final class DashScopeRerankPort implements RerankPort {

    private static final Logger log = LoggerFactory.getLogger(DashScopeRerankPort.class);

    private static final URI DEFAULT_URI =
            URI.create("https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank");

    private static final Pattern RESULT =
            Pattern.compile("\"index\"\\s*:\\s*(\\d+)\\s*,\\s*\"relevance_score\"\\s*:\\s*([0-9.eE+-]+)");

    private static final Pattern RESULT_SCORE_FIRST =
            Pattern.compile("\"relevance_score\"\\s*:\\s*([0-9.eE+-]+)\\s*,\\s*\"index\"\\s*:\\s*(\\d+)");

    private final String apiKey;

    private final String model;

    private final HttpClient http;

    private final URI endpoint;

    public DashScopeRerankPort(String apiKey, String model) {
        this(apiKey, model, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), DEFAULT_URI);
    }

    DashScopeRerankPort(String apiKey, String model, HttpClient http, URI endpoint) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("apiKey required");
        }
        this.apiKey = apiKey.trim();
        this.model = model == null || model.isBlank() ? "gte-rerank-v2" : model.trim();
        this.http = http;
        this.endpoint = endpoint == null ? DEFAULT_URI : endpoint;
    }

    @Override
    public List<RetrieveHit> rerank(String query, List<RetrieveHit> candidates, int topK) {
        if (candidates == null || candidates.isEmpty() || topK <= 0) {
            return List.of();
        }
        if (query == null || query.isBlank()) {
            return List.copyOf(candidates.subList(0, Math.min(topK, candidates.size())));
        }
        String body = buildBody(model, query, candidates, topK);
        try {
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response =
                    http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String bodyPreview = response.body() == null ? "" : response.body();
                if (bodyPreview.length() > 400) {
                    bodyPreview = bodyPreview.substring(0, 400) + "...";
                }
                log.warn(
                        "dashscope rerank http={} model={} queryChars={} body={}",
                        response.statusCode(),
                        model,
                        query.length(),
                        bodyPreview);
                throw new IllegalStateException(
                        "dashscope rerank http " + response.statusCode() + ": " + bodyPreview);
            }
            List<RetrieveHit> ranked = parse(response.body(), candidates, topK);
            log.info(
                    "dashscope rerank model={} in={} out={} topScore={} queryChars={}",
                    model,
                    candidates.size(),
                    ranked.size(),
                    ranked.isEmpty() ? 0.0 : ranked.get(0).score(),
                    query.length());
            return ranked;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("dashscope rerank interrupted", ex);
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("dashscope rerank failed: " + ex.getMessage(), ex);
        }
    }

    static String buildBody(String model, String query, List<RetrieveHit> candidates, int topN) {
        StringBuilder docs = new StringBuilder();
        docs.append('[');
        for (int i = 0; i < candidates.size(); i++) {
            if (i > 0) {
                docs.append(',');
            }
            docs.append('"').append(jsonEscape(candidates.get(i).text())).append('"');
        }
        docs.append(']');
        String modelName = model == null || model.isBlank() ? "gte-rerank-v2" : model;
        return "{\"model\":\""
                + jsonEscape(modelName)
                + "\",\"input\":{\"query\":\""
                + jsonEscape(query)
                + "\",\"documents\":"
                + docs
                + "},\"parameters\":{\"top_n\":"
                + Math.max(1, topN)
                + ",\"return_documents\":false}}";
    }

    static List<RetrieveHit> parse(String json, List<RetrieveHit> candidates, int topK) {
        List<int[]> pairs = new ArrayList<>();
        Matcher m = RESULT.matcher(json == null ? "" : json);
        while (m.find()) {
            pairs.add(new int[] {Integer.parseInt(m.group(1)), encodeScore(Double.parseDouble(m.group(2)))});
        }
        if (pairs.isEmpty()) {
            Matcher alt = RESULT_SCORE_FIRST.matcher(json == null ? "" : json);
            while (alt.find()) {
                pairs.add(new int[] {Integer.parseInt(alt.group(2)), encodeScore(Double.parseDouble(alt.group(1)))});
            }
        }
        if (pairs.isEmpty()) {
            throw new IllegalStateException("dashscope rerank response missing results");
        }
        List<RetrieveHit> out = new ArrayList<>();
        for (int[] pair : pairs) {
            int index = pair[0];
            if (index < 0 || index >= candidates.size()) {
                continue;
            }
            RetrieveHit hit = candidates.get(index);
            out.add(new RetrieveHit(hit.id(), hit.text(), decodeScore(pair[1]), hit.source(), hit.docType()));
            if (out.size() >= topK) {
                break;
            }
        }
        return List.copyOf(out);
    }

    private static int encodeScore(double score) {
        return (int) Math.round(score * 1_000_000);
    }

    private static double decodeScore(int encoded) {
        return encoded / 1_000_000.0;
    }

    static String jsonEscape(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(raw.length() + 8);
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }
}

package com.zhu.scope.adapter.rag;

import com.zhu.scope.knowledge.QueryRewritePort;
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
 * 用短生成把口语问句扩成检索句。失败返回空列表，由 AdvancedRetrievePort 只用原句。
 */
public final class DashScopeQueryRewritePort implements QueryRewritePort {

    private static final Logger log = LoggerFactory.getLogger(DashScopeQueryRewritePort.class);

    private static final URI DEFAULT_URI =
            URI.create("https://dashscope.aliyuncs.com/api/v1/services/aigeneration/text-generation/generation");

    private static final Pattern TEXT_FIELD = Pattern.compile("\"text\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");

    private final String apiKey;

    private final String model;

    private final HttpClient http;

    private final URI endpoint;

    public DashScopeQueryRewritePort(String apiKey, String model) {
        this(apiKey, model, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), DEFAULT_URI);
    }

    DashScopeQueryRewritePort(String apiKey, String model, HttpClient http, URI endpoint) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("apiKey required");
        }
        this.apiKey = apiKey.trim();
        this.model = model == null || model.isBlank() ? "qwen-turbo" : stripProvider(model.trim());
        this.http = http;
        this.endpoint = endpoint == null ? DEFAULT_URI : endpoint;
    }

    @Override
    public List<String> expand(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        String prompt = "你是检索助手。把用户问句改写成 1～2 条更适合知识库检索的短查询。"
                + "每行一条，不要编号、不要解释、不要引号。\n用户问句："
                + query.trim();
        String body = "{\"model\":\""
                + DashScopeRerankPort.jsonEscape(model)
                + "\",\"input\":{\"messages\":[{\"role\":\"user\",\"content\":\""
                + DashScopeRerankPort.jsonEscape(prompt)
                + "\"}]},\"parameters\":{\"result_format\":\"message\",\"max_tokens\":120}}";
        try {
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response =
                    http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("dashscope rewrite http " + response.statusCode());
            }
            List<String> lines = parseLines(response.body());
            log.info(
                    "dashscope rewrite model={} variants={} queryChars={} preview={}",
                    model,
                    lines.size(),
                    query.length(),
                    query.length() <= 80 ? query : query.substring(0, 80) + "...");
            return lines;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("dashscope rewrite interrupted", ex);
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("dashscope rewrite failed: " + ex.getMessage(), ex);
        }
    }

    static List<String> parseLines(String json) {
        String text = extractText(json);
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String line : text.split("\\R")) {
            String trimmed = line.trim().replaceFirst("^\\d+[.)、]\\s*", "");
            if (trimmed.isBlank() || trimmed.length() > 200) {
                continue;
            }
            if (!out.contains(trimmed)) {
                out.add(trimmed);
            }
            if (out.size() >= 2) {
                break;
            }
        }
        return List.copyOf(out);
    }

    static String extractText(String json) {
        if (json == null || json.isBlank()) {
            return "";
        }
        Matcher m = TEXT_FIELD.matcher(json);
        String last = null;
        while (m.find()) {
            last = unescape(m.group(1));
        }
        return last == null ? "" : last;
    }

    private static String unescape(String raw) {
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\\' && i + 1 < raw.length()) {
                char n = raw.charAt(++i);
                switch (n) {
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case '"' -> out.append('"');
                    case '\\' -> out.append('\\');
                    case 'u' -> {
                        if (i + 4 < raw.length()) {
                            out.append((char) Integer.parseInt(raw.substring(i + 1, i + 5), 16));
                            i += 4;
                        }
                    }
                    default -> out.append(n);
                }
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static String stripProvider(String model) {
        if (model.regionMatches(true, 0, "dashscope:", 0, "dashscope:".length())) {
            return model.substring("dashscope:".length()).trim();
        }
        return model;
    }
}

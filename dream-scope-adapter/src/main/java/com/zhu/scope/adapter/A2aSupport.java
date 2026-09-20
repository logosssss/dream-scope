package com.zhu.scope.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * A2A JSON-RPC {@code message/send} 请求编码，以及手写 Card/应答（单测对照）。产品路径走官方 a2a-server。
 */
public final class A2aSupport {

    static final String METHOD_SEND = "message/send";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private A2aSupport() {}

    public static Map<String, Object> agentCard(String publicBaseUrl) {
        String base = trimSlash(publicBaseUrl);
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("protocolVersion", "0.3.0");
        card.put("name", "dream-scope-chat");
        card.put("description", "dream-scope 内置 chat Agent");
        card.put("url", base + "/a2a");
        card.put("version", "0.1.0");
        card.put("preferredTransport", "JSONRPC");
        card.put("defaultInputModes", List.of("text"));
        card.put("defaultOutputModes", List.of("text"));
        card.put("capabilities", Map.of("streaming", false));
        card.put("skills", List.of());
        return card;
    }

    public static Map<String, Object> handleJsonRpc(String body, Function<String, String> onUserText) {
        JsonNode root;
        try {
            root = MAPPER.readTree(body == null ? "" : body);
        } catch (Exception ex) {
            return rpcError(null, -32700, "parse error");
        }
        JsonNode idNode = root.get("id");
        Object id = idNode == null || idNode.isNull() ? null : MAPPER.convertValue(idNode, Object.class);
        String method = text(root.get("method"));
        if (!METHOD_SEND.equals(method)) {
            return rpcError(id, -32601, "method not found: " + method);
        }
        String userText = firstText(root.path("params").path("message").path("parts"));
        if (userText.isBlank()) {
            return rpcError(id, -32602, "text part required");
        }
        String output = onUserText.apply(userText);
        Map<String, Object> part = new LinkedHashMap<>();
        part.put("kind", "text");
        part.put("text", output == null ? "" : output);
        Map<String, Object> artifact = new LinkedHashMap<>();
        artifact.put("artifactId", "a1");
        artifact.put("parts", List.of(part));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", "task-" + UUID.randomUUID());
        result.put("status", Map.of("state", "completed"));
        result.put("artifacts", List.of(artifact));
        Map<String, Object> ok = new LinkedHashMap<>();
        ok.put("jsonrpc", "2.0");
        ok.put("id", id);
        ok.put("result", result);
        return ok;
    }

    public static String requestJson(String text) {
        Map<String, Object> part = Map.of("kind", "text", "text", text == null ? "" : text);
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("kind", "message");
        message.put("messageId", UUID.randomUUID().toString());
        message.put("role", "user");
        message.put("parts", List.of(part));
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("jsonrpc", "2.0");
        req.put("id", "1");
        req.put("method", METHOD_SEND);
        req.put("params", Map.of("message", message));
        try {
            return MAPPER.writeValueAsString(req);
        } catch (Exception ex) {
            throw new IllegalStateException("cannot encode a2a request", ex);
        }
    }

    public static String outputOf(Map<String, Object> response) {
        if (response == null) {
            return "";
        }
        Object result = response.get("result");
        if (!(result instanceof Map<?, ?> resultMap)) {
            return "";
        }
        Object artifacts = resultMap.get("artifacts");
        if (!(artifacts instanceof List<?> list) || list.isEmpty()) {
            return "";
        }
        Object first = list.get(0);
        if (!(first instanceof Map<?, ?> artifact)) {
            return "";
        }
        Object parts = artifact.get("parts");
        if (!(parts instanceof List<?> partList) || partList.isEmpty()) {
            return "";
        }
        Object part = partList.get(0);
        if (!(part instanceof Map<?, ?> partMap)) {
            return "";
        }
        Object text = partMap.get("text");
        return text == null ? "" : String.valueOf(text);
    }

    private static String firstText(JsonNode parts) {
        if (parts == null || !parts.isArray()) {
            return "";
        }
        List<String> texts = new ArrayList<>();
        for (JsonNode part : parts) {
            String kind = text(part.get("kind"));
            if (kind.isEmpty() || "text".equals(kind)) {
                String value = text(part.get("text"));
                if (!value.isEmpty()) {
                    texts.add(value);
                }
            }
        }
        return String.join("", texts);
    }

    private static Map<String, Object> rpcError(Object id, int code, String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("jsonrpc", "2.0");
        error.put("id", id);
        error.put("error", Map.of("code", code, "message", message == null ? "" : message));
        return error;
    }

    private static String text(JsonNode node) {
        return node == null || node.isNull() ? "" : node.asText("");
    }

    private static String trimSlash(String url) {
        if (url == null || url.isBlank()) {
            return "http://127.0.0.1:8091";
        }
        String trimmed = url.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}

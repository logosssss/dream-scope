package com.zhu.scope.boot.agent.bean.request;

import com.zhu.scope.boot.agent.StarterHandler;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** boot 自己的调用入参。不引用 domain。 */
public record StarterRequest(
        String agentId,
        String sessionId,
        String userId,
        String input,
        List<String> imageUrls,
        boolean structured,
        Map<String, Object> jsonSchema) {

    static final int MAX_IMAGE_URLS = 8;

    public StarterRequest(String agentId, String sessionId, String userId, String input) {
        this(agentId, sessionId, userId, input, List.of(), false, null);
    }

    public StarterRequest {
        agentId = agentId == null || agentId.isBlank() ? StarterHandler.CHAT : agentId.trim();
        sessionId = sessionId == null ? "" : sessionId.trim();
        userId = userId == null ? "" : userId.trim();
        input = input == null ? "" : input;
        imageUrls = normalizeImageUrls(imageUrls);
        jsonSchema = copySchema(jsonSchema);
        if (jsonSchema != null) {
            structured = true;
        }
    }

    public boolean hasInput() {
        return !input.isBlank() || !imageUrls.isEmpty();
    }

    private static List<String> normalizeImageUrls(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return List.of();
        }
        List<String> cleaned = new ArrayList<>();
        for (String url : urls) {
            if (url == null || url.isBlank()) {
                continue;
            }
            String trimmed = url.trim();
            if (!httpOrHttps(trimmed)) {
                throw new IllegalArgumentException("image url must be http or https");
            }
            cleaned.add(trimmed);
        }
        if (cleaned.size() > MAX_IMAGE_URLS) {
            throw new IllegalArgumentException("at most " + MAX_IMAGE_URLS + " image urls");
        }
        return List.copyOf(cleaned);
    }

    private static Map<String, Object> copySchema(Map<String, Object> schema) {
        if (schema == null || schema.isEmpty()) {
            return null;
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(schema));
    }

    private static boolean httpOrHttps(String url) {
        try {
            String scheme = URI.create(url).getScheme();
            if (scheme == null) {
                return false;
            }
            String normalized = scheme.toLowerCase(Locale.ROOT);
            return "http".equals(normalized) || "https".equals(normalized);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}

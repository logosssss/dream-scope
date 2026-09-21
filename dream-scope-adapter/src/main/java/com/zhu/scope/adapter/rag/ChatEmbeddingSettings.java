package com.zhu.scope.adapter.rag;

/**
 * DashScope embedding，与对话 {@code dream-scope.model.default} 分开。
 * web 从 {@code dream-scope.rag.embedding-model} / {@code embedding-dimensions} 填入。
 */
public record ChatEmbeddingSettings(String modelName, int dimensions) {

    public static final String DEFAULT_MODEL = "text-embedding-v3";

    public static final int DEFAULT_DIMENSIONS = 1024;

    public ChatEmbeddingSettings {
        modelName = normalizeModel(modelName);
        if (dimensions <= 0) {
            dimensions = DEFAULT_DIMENSIONS;
        }
    }

    public static ChatEmbeddingSettings defaults() {
        return new ChatEmbeddingSettings(DEFAULT_MODEL, DEFAULT_DIMENSIONS);
    }

    static String normalizeModel(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return DEFAULT_MODEL;
        }
        String trimmed = modelName.trim();
        if (trimmed.regionMatches(true, 0, "dashscope:", 0, "dashscope:".length())) {
            String bare = trimmed.substring("dashscope:".length()).trim();
            return bare.isEmpty() ? DEFAULT_MODEL : bare;
        }
        return trimmed;
    }
}

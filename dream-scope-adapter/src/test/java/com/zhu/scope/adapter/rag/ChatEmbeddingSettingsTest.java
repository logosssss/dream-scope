package com.zhu.scope.adapter.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ChatEmbeddingSettingsTest {

    @Test
    void blankUsesDefaults() {
        ChatEmbeddingSettings settings = new ChatEmbeddingSettings("  ", 0);
        assertEquals(ChatEmbeddingSettings.DEFAULT_MODEL, settings.modelName());
        assertEquals(ChatEmbeddingSettings.DEFAULT_DIMENSIONS, settings.dimensions());
    }

    @Test
    void stripsDashScopePrefix() {
        ChatEmbeddingSettings settings = new ChatEmbeddingSettings("dashscope:text-embedding-v2", 768);
        assertEquals("text-embedding-v2", settings.modelName());
        assertEquals(768, settings.dimensions());
    }

    @Test
    void keepsBareModelName() {
        assertEquals("text-embedding-v4", new ChatEmbeddingSettings(" text-embedding-v4 ", 512).modelName());
    }
}

package com.zhu.scope.adapter.nacos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

class ChatNacosPromptMiddlewareTest {

    @Test
    void appendsLatestPromptOnce() {
        ChatNacosClient nacos = mock(ChatNacosClient.class);
        when(nacos.sysPrompt(null)).thenReturn("from-nacos");
        ChatNacosPromptMiddleware middleware = new ChatNacosPromptMiddleware(nacos);
        assertEquals("base\nfrom-nacos", middleware.onSystemPrompt(null, null, "base").block());
        assertEquals("base\nfrom-nacos", middleware.onSystemPrompt(null, null, "base\nfrom-nacos").block());
    }

    @Test
    void keepsCurrentWhenNacosEmpty() {
        ChatNacosClient nacos = mock(ChatNacosClient.class);
        when(nacos.sysPrompt(null)).thenReturn(null);
        ChatNacosPromptMiddleware middleware = new ChatNacosPromptMiddleware(nacos);
        assertEquals("base", middleware.onSystemPrompt(null, null, "base").block());
    }
}

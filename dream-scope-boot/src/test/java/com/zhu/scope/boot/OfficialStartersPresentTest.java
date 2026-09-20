package com.zhu.scope.boot;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

class OfficialStartersPresentTest {

    @Test
    void allOfficialStarterAutoConfigurationsAreOnClasspath() {
        assertDoesNotThrow(() -> Class.forName("io.agentscope.spring.boot.AgentscopeAutoConfiguration"));
        assertDoesNotThrow(() -> Class.forName("io.agentscope.spring.boot.dashscope.DashScopeAutoConfiguration"));
        assertDoesNotThrow(() -> Class.forName("io.agentscope.spring.boot.openai.OpenAIAutoConfiguration"));
        assertDoesNotThrow(() -> Class.forName("io.agentscope.spring.boot.anthropic.AnthropicAutoConfiguration"));
        assertDoesNotThrow(() -> Class.forName("io.agentscope.spring.boot.gemini.GeminiAutoConfiguration"));
        assertDoesNotThrow(() -> Class.forName("io.agentscope.spring.boot.ollama.OllamaAutoConfiguration"));
        assertDoesNotThrow(
                () -> Class.forName("io.agentscope.spring.boot.chat.config.ChatCompletionsWebAutoConfiguration"));
        assertDoesNotThrow(
                () -> Class.forName("io.agentscope.spring.boot.agui.mvc.AgentscopeAguiMvcAutoConfiguration"));
        assertDoesNotThrow(() -> Class.forName("io.agentscope.spring.boot.admin.AgentscopeAdminAutoConfiguration"));
        assertDoesNotThrow(() -> Class.forName("io.agentscope.spring.boot.a2a.AgentscopeA2aAutoConfiguration"));
        assertDoesNotThrow(
                () -> Class.forName("io.agentscope.spring.boot.nacos.AgentscopeA2aNacosAutoConfiguration"));
    }
}

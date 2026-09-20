package com.zhu.scope.boot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.zhu.scope.agent.AgentHandler;
import com.zhu.scope.agent.AgentIds;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.a2a.server.AgentScopeA2aServer;
import io.agentscope.core.model.Model;
import io.agentscope.spring.boot.admin.registry.AgentRegistry;
import io.agentscope.spring.boot.chat.web.ChatCompletionsController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@Import(StarterAutoConfigTest.FakeModelConfig.class)
@TestPropertySource(
        properties = {
            "agentscope.agent.enabled=true",
            "agentscope.agent.name=chat",
            "agentscope.dashscope.enabled=false",
            "agentscope.nacos.prompt.enabled=false"
        })
class StarterAutoConfigTest {

    @Autowired
    ReActAgent agentscopeReActAgent;

    @Autowired
    AgentHandler starterChatAgent;

    @Autowired
    ChatCompletionsController chatCompletionsController;

    @Autowired
    AgentScopeA2aServer agentScopeA2aServer;

    @Autowired
    AgentRegistry agentscopeAgentRegistry;

    @Test
    void officialStarterWiresReActAgentIntoDomainHandler() {
        assertEquals("chat", agentscopeReActAgent.getName());
        assertInstanceOf(StarterChatAgent.class, starterChatAgent);
        assertEquals(AgentIds.CHAT, starterChatAgent.id());
        assertNotNull(chatCompletionsController);
        assertNotNull(agentScopeA2aServer);
        assertNotNull(agentscopeAgentRegistry);
    }

    @TestConfiguration
    static class FakeModelConfig {
        @Bean
        Model dashScopeChatModel() {
            return mock(Model.class);
        }
    }
}

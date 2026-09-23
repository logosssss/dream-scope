package com.zhu.scope.boot.mcp;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.zhu.scope.boot.knowledge.tool.BootRetrieveTool;
import com.zhu.scope.boot.mcp.server.BootMcpDemo;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@Import(BootMcpWiringTest.FakeModelConfig.class)
@TestPropertySource(
        properties = {
            "agentscope.agent.enabled=true",
            "agentscope.dashscope.enabled=false",
            "agentscope.nacos.prompt.enabled=false",
            "agentscope.a2a.server.enabled=false",
            "agentscope.admin.enabled=false",
            "dream-scope.mcp.demo-enabled=true",
            "dream-scope.mcp.demo-port=18095",
            "dream-scope.knowledge.api-key=",
            "dream-scope.knowledge.pg.jdbc-url="
        })
class BootMcpWiringTest {

    @Autowired
    Toolkit agentscopeToolkit;

    @Test
    void starterToolkitContainsDemoTool() {
        assertTrue(agentscopeToolkit.getToolNames().contains(BootMcpDemo.TOOL));
        assertTrue(agentscopeToolkit.getToolNames().contains(BootRetrieveTool.NAME));
    }

    @TestConfiguration
    static class FakeModelConfig {
        @Bean
        Model dashScopeChatModel() {
            return mock(Model.class);
        }
    }
}

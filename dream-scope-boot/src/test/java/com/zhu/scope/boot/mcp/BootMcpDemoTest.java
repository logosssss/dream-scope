package com.zhu.scope.boot.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zhu.scope.boot.mcp.server.BootMcpDemo;
import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class BootMcpDemoTest {

    @Test
    void clientListsEchoTool() {
        BootMcpDemo server = BootMcpDemo.start(18094);
        McpClientWrapper client = null;
        try {
            client = McpClientBuilder.create("boot")
                    .streamableHttpTransport(server.url())
                    .timeout(Duration.ofSeconds(10))
                    .initializationTimeout(Duration.ofSeconds(10))
                    .buildAsync()
                    .block(Duration.ofSeconds(10));
            client.initialize().block(Duration.ofSeconds(10));
            var tools = client.listTools().block(Duration.ofSeconds(10));
            assertEquals(List.of(BootMcpDemo.TOOL), tools.stream().map(tool -> tool.name()).toList());
            assertTrue(Boolean.TRUE.equals(tools.get(0).annotations().readOnlyHint()));
        } finally {
            if (client != null) {
                client.close();
            }
            server.close();
        }
    }
}

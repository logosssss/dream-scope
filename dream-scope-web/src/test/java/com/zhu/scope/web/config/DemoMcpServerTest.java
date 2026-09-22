package com.zhu.scope.web.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zhu.scope.adapter.mcp.ChatMcp;
import com.zhu.scope.adapter.mcp.ChatMcpServer;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DemoMcpServerTest {

    @Test
    void clientListsEchoTool() {
        DemoMcpServer server = DemoMcpServer.start(18092);
        try {
            var clients = ChatMcp.openAll(List.of(new ChatMcpServer(
                    "demo",
                    "streamableHttp",
                    "http://127.0.0.1:18092/mcp",
                    null,
                    List.of(),
                    Map.of(),
                    Map.of(),
                    Duration.ofSeconds(10))));
            try {
                assertFalse(clients.isEmpty());
                clients.get(0).initialize().block(Duration.ofSeconds(10));
                var tools = clients.get(0).listTools().block(Duration.ofSeconds(10));
                assertEquals(List.of("mcp__demo__echo"), tools.stream().map(tool -> tool.name()).toList());
                assertTrue(Boolean.TRUE.equals(tools.get(0).annotations().readOnlyHint()));
            } finally {
                ChatMcp.closeQuietly(clients);
            }
        } finally {
            server.close();
        }
    }
}

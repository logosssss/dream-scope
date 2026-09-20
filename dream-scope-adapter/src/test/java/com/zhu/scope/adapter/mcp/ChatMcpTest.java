package com.zhu.scope.adapter.mcp;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class ChatMcpTest {

    @Test
    void builderOfAcceptsThreeTransports() {
        assertDoesNotThrow(() -> ChatMcp.builderOf(http("remote-http", "streamableHttp", "http://127.0.0.1:8082/mcp")));
        assertDoesNotThrow(() -> ChatMcp.builderOf(http("remote-search", "sse", "http://127.0.0.1:8081/sse")));
        assertDoesNotThrow(
                () -> ChatMcp.builderOf(
                        new ChatMcpServer(
                                "filesystem",
                                "stdio",
                                null,
                                "npx",
                                List.of("-y", "@modelcontextprotocol/server-filesystem", "/tmp"),
                                Map.of(),
                                Map.of(),
                                Duration.ofSeconds(10))));
    }

    @Test
    void builderOfRejectsIncompleteSpecs() {
        assertThrows(IllegalArgumentException.class, () -> ChatMcp.builderOf(http("x", "stdio", "http://127.0.0.1/mcp")));
        assertThrows(
                IllegalArgumentException.class, () -> ChatMcp.builderOf(http("x", "streamableHttp", "file:///tmp")));
        assertThrows(IllegalArgumentException.class, () -> ChatMcp.builderOf(http("x", "grpc", "http://127.0.0.1/mcp")));
        assertThrows(
                IllegalArgumentException.class,
                () -> ChatMcp.builderOf(
                        new ChatMcpServer(null, "sse", "http://127.0.0.1/sse", null, List.of(), Map.of(), Map.of(), null)));
    }

    @Test
    void registerPullsSchemaFromClient() {
        McpClientWrapper mcp = mock(McpClientWrapper.class);
        when(mcp.getName()).thenReturn("remote-http");
        when(mcp.isInitialized()).thenReturn(true);
        when(mcp.initialize()).thenReturn(Mono.empty());
        when(mcp.listTools()).thenReturn(Mono.just(List.of()));
        Toolkit toolkit = new Toolkit();
        ChatMcp.register(toolkit, List.of(mcp));
        verify(mcp).listTools();
        assertTrue(toolkit.getToolNames() != null);
    }

    @Test
    void closeQuietlyIgnoresNullAndFailures() {
        McpClientWrapper ok = mock(McpClientWrapper.class);
        McpClientWrapper boom = mock(McpClientWrapper.class);
        org.mockito.Mockito.doThrow(new IllegalStateException("closed")).when(boom).close();
        ChatMcp.closeQuietly(java.util.Arrays.asList(ok, boom, null));
        verify(ok).close();
        verify(boom).close();
    }

    private static ChatMcpServer http(String name, String transport, String url) {
        return new ChatMcpServer(
                name, transport, url, null, List.of(), Map.of(), Map.of("Authorization", "Bearer t"), null);
    }
}

package com.zhu.scope.adapter.mcp;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 一条 MCP Server 声明。web 从 {@code dream-scope.mcp.servers} 填入，{@link ChatMcp} 再调官方
 * {@code McpClientBuilder}。本类型不含 {@code io.agentscope}。
 */
public record ChatMcpServer(
        String name,
        String transport,
        String url,
        String command,
        List<String> args,
        Map<String, String> env,
        Map<String, String> headers,
        Duration timeout) {

    public ChatMcpServer {
        name = blankToNull(name);
        transport = transport == null || transport.isBlank() ? "streamableHttp" : transport.trim();
        url = blankToNull(url);
        command = blankToNull(command);
        args = args == null ? List.of() : List.copyOf(args);
        env = env == null ? Map.of() : Map.copyOf(env);
        headers = headers == null ? Map.of() : Map.copyOf(headers);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

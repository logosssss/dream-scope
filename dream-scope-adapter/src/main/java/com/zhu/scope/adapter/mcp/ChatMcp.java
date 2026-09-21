package com.zhu.scope.adapter.mcp;

import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 官方 MCP 客户端：{@link McpClientBuilder} 建连，{@link Toolkit#registerMcpClient} 挂到 Agent。
 *
 * <p>三种传输都走 builder：{@code stdio} / {@code sse} / {@code streamableHttp}。应用关闭时 {@link #closeQuietly}
 * 回收连接（stdio 子进程尤其要关）。
 */
public final class ChatMcp {

    private static final Logger log = LoggerFactory.getLogger(ChatMcp.class);

    static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private ChatMcp() {}

    static McpClientBuilder builderOf(ChatMcpServer spec) {
        Objects.requireNonNull(spec, "mcp server");
        if (spec.name() == null) {
            throw new IllegalArgumentException("mcp server requires name");
        }
        String transport = spec.transport().toLowerCase(Locale.ROOT);
        McpClientBuilder builder = McpClientBuilder.create(spec.name());
        if ("stdio".equals(transport)) {
            if (spec.command() == null) {
                throw new IllegalArgumentException("mcp stdio requires command: " + spec.name());
            }
            if (spec.env().isEmpty()) {
                builder.stdioTransport(spec.command(), spec.args().toArray(String[]::new));
            } else {
                builder.stdioTransport(spec.command(), spec.args(), spec.env());
            }
        } else if ("sse".equals(transport)) {
            builder.sseTransport(requireHttpUrl(spec));
            applyHttpOptions(builder, spec);
        } else if ("streamablehttp".equals(transport) || "streamable-http".equals(transport)) {
            builder.streamableHttpTransport(requireHttpUrl(spec));
            applyHttpOptions(builder, spec);
        } else {
            throw new IllegalArgumentException("mcp transport must be stdio, sse or streamableHttp: " + spec.name());
        }
        Duration timeout = spec.timeout() == null || spec.timeout().isZero() || spec.timeout().isNegative()
                ? DEFAULT_TIMEOUT
                : spec.timeout();
        builder.timeout(timeout).initializationTimeout(timeout);
        return builder;
    }

    public static List<McpClientWrapper> openAll(List<ChatMcpServer> specs) {
        if (specs == null || specs.isEmpty()) {
            return List.of();
        }
        List<McpClientWrapper> opened = new ArrayList<>();
        try {
            for (ChatMcpServer spec : specs) {
                if (spec == null || spec.name() == null) {
                    continue;
                }
                log.info("mcp connect start name={} transport={}", spec.name(), spec.transport());
                McpClientWrapper client = builderOf(spec).buildAsync().block(blockTimeout(spec));
                if (client == null) {
                    throw new IllegalStateException("mcp handshake returned null: " + spec.name());
                }
                log.info("mcp connect ok name={}", spec.name());
                opened.add(client);
            }
            return List.copyOf(opened);
        } catch (RuntimeException ex) {
            closeQuietly(opened);
            throw new IllegalStateException("mcp connect failed", ex);
        }
    }

    public static void register(Toolkit toolkit, List<McpClientWrapper> clients) {
        Objects.requireNonNull(toolkit, "toolkit");
        if (clients == null || clients.isEmpty()) {
            return;
        }
        for (McpClientWrapper client : clients) {
            if (client == null) {
                continue;
            }
            try {
                toolkit.registerMcpClient(client).block(DEFAULT_TIMEOUT);
                log.info("mcp register ok name={}", client.getName());
            } catch (RuntimeException ex) {
                throw new IllegalStateException("mcp register failed: " + client.getName(), ex);
            }
        }
    }

    public static void closeQuietly(List<McpClientWrapper> clients) {
        if (clients == null || clients.isEmpty()) {
            return;
        }
        for (int i = clients.size() - 1; i >= 0; i--) {
            McpClientWrapper client = clients.get(i);
            if (client == null) {
                continue;
            }
            try {
                client.close();
            } catch (RuntimeException ignored) {
                // 关闭阶段不掩盖主异常
            }
        }
    }

    private static void applyHttpOptions(McpClientBuilder builder, ChatMcpServer spec) {
        if (!spec.headers().isEmpty()) {
            builder.headers(spec.headers());
        }
    }

    private static String requireHttpUrl(ChatMcpServer spec) {
        if (spec.url() == null) {
            throw new IllegalArgumentException("mcp server requires url: " + spec.name());
        }
        if (!spec.url().startsWith("http://") && !spec.url().startsWith("https://")) {
            throw new IllegalArgumentException("mcp url must be http/https: " + spec.name());
        }
        return spec.url();
    }

    private static Duration blockTimeout(ChatMcpServer spec) {
        Duration timeout = spec.timeout();
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            return DEFAULT_TIMEOUT;
        }
        return timeout;
    }
}

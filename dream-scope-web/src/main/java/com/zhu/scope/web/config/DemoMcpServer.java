package com.zhu.scope.web.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.servlet.http.HttpServlet;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.Tomcat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 本机演示 MCP。工具名就是 {@code mcp__demo__echo}（AgentScope 原样注册，不会再加前缀）。
 * 标成只读，避免对话停在人工确认上。
 * 必须先于 chat 的 {@code McpClientBuilder} 开始监听。
 */
public final class DemoMcpServer implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(DemoMcpServer.class);

    private final Tomcat tomcat;

    private final McpSyncServer server;

    private final int port;

    private DemoMcpServer(Tomcat tomcat, McpSyncServer server, int port) {
        this.tomcat = tomcat;
        this.server = server;
        this.port = port;
    }

    static DemoMcpServer disabled() {
        return new DemoMcpServer(null, null, 0);
    }

    static DemoMcpServer start(int port) {
        if (port <= 0) {
            throw new IllegalArgumentException("mcp demo port required");
        }
        McpJsonMapper json = new JacksonMcpJsonMapper(new ObjectMapper());
        HttpServletStreamableServerTransportProvider transport = HttpServletStreamableServerTransportProvider.builder()
                .jsonMapper(json)
                .mcpEndpoint("/mcp")
                .build();
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name("mcp__demo__echo")
                .description("把 text 原样返回，用来确认 MCP 已接通")
                .inputSchema(json, "{\"type\":\"object\",\"properties\":{\"text\":{\"type\":\"string\"}},\"required\":[\"text\"]}")
                .annotations(new McpSchema.ToolAnnotations(null, Boolean.TRUE, Boolean.FALSE, Boolean.TRUE, Boolean.FALSE, null))
                .build();
        McpSyncServer mcp = McpServer.sync(transport)
                .serverInfo("dream-scope-demo", "0.1.0")
                .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
                .tool(tool, (exchange, args) -> {
                    Object text = args == null ? "" : args.get("text");
                    return new McpSchema.CallToolResult("demo echo: " + (text == null ? "" : text), false);
                })
                .build();
        Tomcat tomcat = listen(port, transport);
        log.info("mcp demo listening port={} tool=mcp__demo__echo", port);
        return new DemoMcpServer(tomcat, mcp, port);
    }

    int port() {
        return port;
    }

    private static Tomcat listen(int port, HttpServlet servlet) {
        try {
            Path base = Files.createTempDirectory("dream-scope-mcp-");
            Tomcat tomcat = new Tomcat();
            tomcat.setBaseDir(base.toString());
            tomcat.setHostname("127.0.0.1");
            tomcat.setPort(port);
            tomcat.getConnector();
            Context context = tomcat.addContext("", base.toString());
            Wrapper wrapper = Tomcat.addServlet(context, "mcp", servlet);
            wrapper.setAsyncSupported(true);
            context.addServletMappingDecoded("/mcp", "mcp");
            tomcat.start();
            return tomcat;
        } catch (Exception ex) {
            throw new IllegalStateException("mcp demo failed to listen on " + port, ex);
        }
    }

    @Override
    public void close() {
        if (server != null) {
            server.close();
        }
        if (tomcat != null) {
            try {
                tomcat.stop();
                tomcat.destroy();
            } catch (Exception ex) {
                log.warn("mcp demo stop failed: {}", ex.getMessage());
            }
        }
    }
}

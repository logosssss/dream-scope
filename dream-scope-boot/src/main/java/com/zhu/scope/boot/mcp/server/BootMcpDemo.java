package com.zhu.scope.boot.mcp.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.servlet.http.HttpServlet;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.Tomcat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** boot 进程内的演示 MCP。工具名 mcp__boot__echo，标成只读。 */
public final class BootMcpDemo implements AutoCloseable {

    public static final String TOOL = "mcp__boot__echo";

    private static final Logger log = LoggerFactory.getLogger(BootMcpDemo.class);

    private final Tomcat tomcat;

    private final McpSyncServer server;

    private final int port;

    private McpClientWrapper client;

    private BootMcpDemo(Tomcat tomcat, McpSyncServer server, int port) {
        this.tomcat = tomcat;
        this.server = server;
        this.port = port;
    }

    public static BootMcpDemo start(int port) {
        if (port <= 0) {
            throw new IllegalArgumentException("mcp demo port required");
        }
        McpJsonMapper json = new JacksonMcpJsonMapper(new ObjectMapper());
        HttpServletStreamableServerTransportProvider transport = HttpServletStreamableServerTransportProvider.builder()
                .jsonMapper(json)
                .mcpEndpoint("/mcp")
                .build();
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name(TOOL)
                .description("把 text 原样返回，用来确认 MCP 已接通")
                .inputSchema(json, "{\"type\":\"object\",\"properties\":{\"text\":{\"type\":\"string\"}},\"required\":[\"text\"]}")
                .annotations(new McpSchema.ToolAnnotations(null, Boolean.TRUE, Boolean.FALSE, Boolean.TRUE, Boolean.FALSE, null))
                .build();
        McpSyncServer mcp = McpServer.sync(transport)
                .serverInfo("dream-scope-boot", "0.1.0")
                .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
                .tool(tool, (exchange, args) -> {
                    Object text = args == null ? "" : args.get("text");
                    return new McpSchema.CallToolResult("boot echo: " + (text == null ? "" : text), false);
                })
                .build();
        Tomcat tomcat = listen(port, transport);
        log.info("boot mcp demo listening port={} tool={}", port, TOOL);
        return new BootMcpDemo(tomcat, mcp, port);
    }

    int port() {
        return port;
    }

    public String url() {
        return "http://127.0.0.1:" + port + "/mcp";
    }

    public void bind(McpClientWrapper client) {
        this.client = client;
    }

    private static Tomcat listen(int port, HttpServlet servlet) {
        try {
            Path base = Files.createTempDirectory("dream-scope-boot-mcp-");
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
            throw new IllegalStateException("boot mcp demo failed to listen on " + port, ex);
        }
    }

    @Override
    public void close() {
        if (client != null) {
            try {
                client.close();
            } catch (RuntimeException ex) {
                log.warn("boot mcp client close failed: {}", ex.getMessage());
            }
        }
        if (server != null) {
            server.close();
        }
        if (tomcat != null) {
            try {
                tomcat.stop();
                tomcat.destroy();
            } catch (Exception ex) {
                log.warn("boot mcp demo stop failed: {}", ex.getMessage());
            }
        }
    }
}

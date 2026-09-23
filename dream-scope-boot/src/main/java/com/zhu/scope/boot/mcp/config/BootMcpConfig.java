package com.zhu.scope.boot.mcp.config;

import com.zhu.scope.boot.config.BootScopeProperties;
import com.zhu.scope.boot.mcp.server.BootMcpDemo;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 演示 MCP 先监听，再挂到 starter 要用的 Toolkit 上。
 * bean 名与官方 starter 相同，用来替换它的空 Toolkit。
 */
@Configuration
@ConditionalOnProperty(prefix = "dream-scope.mcp", name = "demo-enabled", havingValue = "true", matchIfMissing = true)
public class BootMcpConfig {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Bean(destroyMethod = "close")
    BootMcpDemo bootMcpDemo(BootScopeProperties props) {
        return BootMcpDemo.start(props.getMcp().getDemoPort());
    }

    @Bean(name = "agentscopeToolkit")
    Toolkit agentscopeToolkit(BootMcpDemo demo) {
        Toolkit toolkit = new Toolkit();
        McpClientWrapper client = McpClientBuilder.create("boot")
                .streamableHttpTransport(demo.url())
                .timeout(TIMEOUT)
                .initializationTimeout(TIMEOUT)
                .buildAsync()
                .block(TIMEOUT);
        if (client == null) {
            throw new IllegalStateException("boot mcp handshake returned null");
        }
        try {
            client.initialize().block(TIMEOUT);
            toolkit.registerMcpClient(client).block(TIMEOUT);
            demo.bind(client);
        } catch (RuntimeException ex) {
            client.close();
            throw ex;
        }
        return toolkit;
    }
}

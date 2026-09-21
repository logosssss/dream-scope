package com.zhu.scope.web.config;

import com.zhu.scope.adapter.ChatHarnessOptions;
import com.zhu.scope.adapter.ScopeChatAgent;
import com.zhu.scope.adapter.mcp.ChatMcpServer;
import com.zhu.scope.adapter.nacos.ChatNacosClient;
import com.zhu.scope.agent.AgentHandler;
import com.zhu.scope.knowledge.RetrievePort;
import com.zhu.scope.web.util.WorkspaceSubagentSeed;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 内置 {@code chat}。工作区落盘后再建 Harness。 */
@Configuration
public class ChatPortsConfig {

    private static final Logger log = LoggerFactory.getLogger(ChatPortsConfig.class);

    /**
     * {@code destroyMethod = "close"}：Harness → MCP → Jedis。
     *
     * <p>工作区必须在 {@link ScopeChatAgent#create} 之前落盘。MCP 走 {@code McpClientBuilder}，
     * {@code tools.json} 的 mcpServers 保持空，避免 Harness 再连一次。
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(name = "chatAgentHandler")
    AgentHandler chatAgentHandler(
            DreamScopeProperties props, RetrievePort retrievePort, ObjectProvider<ChatNacosClient> nacos) {
        String modelId = ScopeChatAgent.resolveModelId(props.getModel().getChat(), props.getModel().getDefault());
        String apiKey = props.getModel().apiKeyFor(modelId);
        WorkspaceSubagentSeed.copyBundled(props.getWorkspaceDir());
        WorkspaceSubagentSeed.applyMcpServers(props.getWorkspaceDir(), List.of());
        String fallbackId = props.getModel().getFallback();
        String fallbackKey =
                fallbackId == null || fallbackId.isBlank()
                        ? null
                        : props.getModel().fallbackApiKeyFor(fallbackId);
        ChatNacosClient nacosClient = nacos.getIfAvailable();
        List<ChatMcpServer> servers = mcpServers(props);
        log.info(
                "chat bean assemble model={} fallback={} hasApiKey={} workspace={} timeout={} planMode={} nacos={} mcpServers={}",
                modelId,
                fallbackId,
                apiKey != null && !apiKey.isBlank(),
                props.getWorkspaceDir(),
                props.getChatTimeout(),
                props.getPlanMode().isEnabled(),
                nacosClient != null,
                servers.size());
        ChatHarnessOptions options = new ChatHarnessOptions(
                props.getChatTimeout(),
                props.getWorkspaceDir(),
                props.getCompaction().getTriggerMessages(),
                props.getCompaction().getKeepMessages(),
                props.getRedis().getUri(),
                props.getRedis().getKeyPrefix(),
                props.getModel().getTemperature(),
                props.getModel().getTopP(),
                props.getModel().getMaxTokens(),
                fallbackId,
                fallbackKey,
                props.getPlanMode().isEnabled(),
                props.getPlanMode().getDirectory(),
                null);
        return ScopeChatAgent.create(modelId, apiKey, options, retrievePort, servers, nacosClient);
    }

    private static List<ChatMcpServer> mcpServers(DreamScopeProperties props) {
        List<ChatMcpServer> out = new ArrayList<>();
        for (DreamScopeProperties.McpServerSettings server : props.getMcp().getServers()) {
            if (server == null) {
                continue;
            }
            out.add(new ChatMcpServer(
                    server.getName(),
                    server.getTransport(),
                    server.getUrl(),
                    server.getCommand(),
                    server.getArgs(),
                    server.getEnv(),
                    server.getHeaders(),
                    server.getTimeout()));
        }
        return List.copyOf(out);
    }
}

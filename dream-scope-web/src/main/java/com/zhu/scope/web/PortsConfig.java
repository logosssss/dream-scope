package com.zhu.scope.web;

import com.zhu.scope.adapter.ChatHarnessOptions;
import com.zhu.scope.adapter.ScopeA2aClientAgent;
import com.zhu.scope.adapter.ScopeChatAgent;
import com.zhu.scope.adapter.ScopeKnowledgeAgent;
import com.zhu.scope.adapter.a2a.ScopeA2aServer;
import com.zhu.scope.adapter.mcp.ChatMcpServer;
import com.zhu.scope.adapter.rag.SimpleKnowledgeRetrievePort;
import com.zhu.scope.agent.AgentHandler;
import com.zhu.scope.agent.AgentRegistry;
import com.zhu.scope.agent.InMemoryAgentRegistry;
import com.zhu.scope.knowledge.RetrievePort;
import com.zhu.scope.rag.InMemoryKeywordIndex;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * web 组合根：把 {@code dream-scope.*} 和环境变量收成 domain {@link AgentHandler}，再交给 {@link AgentRegistry}。
 *
 * <p>本类是产品入口（8091）唯一允许「知道 adapter」的地方。Controller 只认 {@link AgentHandler} /
 * {@link AgentRegistry}，不碰 {@code io.agentscope}。官方 starter 在 {@code dream-scope-boot}，不经过这里。
 *
 * <h2>装配顺序（chat）</h2>
 *
 * <pre>
 * DreamScopeProperties + Environment
 *   → WorkspaceSubagentSeed.copyBundled / applyMcpServers   // 工作区落盘
 *   → ChatHarnessOptions
 *   → ScopeChatAgent.create（Redis ping、HarnessAgent.build）
 * </pre>
 *
 * <h2>Bean 名与测试</h2>
 *
 * <p>{@code chatAgentHandler} / {@code knowledgeAgentHandler} / {@code a2aAgentHandler} 带
 * {@link ConditionalOnMissingBean}：web 单测用 {@code @TestBean(name = "chatAgentHandler")} 换桩，避免连 Redis / 模型。
 *
 * <p>模型 Key <em>不</em>走 {@code dream-scope.*}，而走 {@link ScopeChatAgent#apiKeyProperty} 对应的环境变量
 *（{@code DASHSCOPE_API_KEY} 等）。进程不会自动加载 {@code .env}。
 */
@Configuration
@EnableConfigurationProperties(DreamScopeProperties.class)
public class PortsConfig {

    /**
     * 内置 {@code chat}。{@code destroyMethod = "close"}：Harness → MCP → Jedis。
     *
     * <p>工作区必须在 {@link ScopeChatAgent#create} 之前落盘：{@code .agentscope/} 已 gitignore，Harness 要扫
     * {@code AGENTS.md} / {@code skills/} / {@code subagents/} / {@code tools.json}。MCP 走
     * {@code McpClientBuilder} 注册 Toolkit，{@code tools.json} 的 mcpServers 保持空，避免 Harness 再连一次。
     *
     * <p>{@link RetrievePort} 进 {@link ScopeChatAgent#create} 的第四参，注册 chat 工具 {@code retrieve}；与下面的
     * {@code knowledge} Handler 共用同一个索引 Bean。
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(name = "chatAgentHandler")
    AgentHandler chatAgentHandler(DreamScopeProperties props, Environment env, RetrievePort retrievePort) {
        String modelId = ScopeChatAgent.resolveModelId(props.getModel().getChat(), props.getModel().getDefault());
        String apiKey = env.getProperty(ScopeChatAgent.apiKeyProperty(modelId));
        WorkspaceSubagentSeed.copyBundled(props.getWorkspaceDir());
        WorkspaceSubagentSeed.applyMcpServers(props.getWorkspaceDir(), List.of());
        String fallbackId = props.getModel().getFallback();
        String fallbackKey =
                fallbackId == null || fallbackId.isBlank()
                        ? null
                        : env.getProperty(ScopeChatAgent.apiKeyProperty(fallbackId));
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
                props.getPlanMode().getDirectory());
        return ScopeChatAgent.create(modelId, apiKey, options, retrievePort, mcpServers(props));
    }

    /**
     * 内置 {@code knowledge}：只检索、不调模型。HTTP {@code agentId=knowledge} 走这里，不经过 Harness。
     */
    @Bean
    @ConditionalOnMissingBean(name = "knowledgeAgentHandler")
    AgentHandler knowledgeAgentHandler(RetrievePort retrievePort) {
        return new ScopeKnowledgeAgent(retrievePort);
    }

    /**
     * 官方 A2A Server：Agent Card + JSON-RPC。不引入 a2a Spring starter（对照入口在 boot）。
     */
    @Bean
    @ConditionalOnMissingBean
    ScopeA2aServer scopeA2aServer(
            @Qualifier("chatAgentHandler") AgentHandler chatAgentHandler, DreamScopeProperties props) {
        return ScopeA2aServer.create(chatAgentHandler, props.getA2a().getPublicUrl());
    }

    @Bean
    ApplicationListener<ApplicationReadyEvent> a2aPostEndpointReady(
            ScopeA2aServer server, DreamScopeProperties props) {
        return event -> {
            if (props.getA2a().isEnabled()) {
                server.postEndpointReady();
            }
        };
    }

    /**
     * 可选 {@code a2a} 客户端：官方 {@code A2aAgent} 调远端 well-known Card。
     * 没配 remote-url 则不注册，{@code agentId=a2a} 会 404。本机 Card / {@code POST /a2a} 在 {@code A2aController}。
     */
    @Bean
    @ConditionalOnProperty(prefix = "dream-scope.a2a", name = "remote-url")
    @ConditionalOnMissingBean(name = "a2aAgentHandler")
    AgentHandler a2aAgentHandler(DreamScopeProperties props) {
        return new ScopeA2aClientAgent(props.getA2a().getRemoteUrl());
    }

    /**
     * 按 {@link AgentHandler#id()} 注册。Spring 会注入容器里所有 Handler（chat 必有，knowledge 必有，a2a 视配置）。
     * {@code AgentInvokeController} 只通过本 Bean 查找，不直接依赖具体实现类。
     */
    @Bean
    AgentRegistry agentRegistry(List<AgentHandler> handlers) {
        return new InMemoryAgentRegistry(handlers);
    }

    /**
     * chat 的 {@code retrieve} 与 {@code knowledge} Handler 共用。换实现只换这个 Bean。
     *
     * <p>{@code dream-scope.rag.provider=auto}（默认）：有 {@code DASHSCOPE_API_KEY} 走官方
     * {@link SimpleKnowledgeRetrievePort}（SimpleKnowledge + InMemoryStore + text-embedding-v3），否则关键词索引。
     * {@code simple} 无 Key 则启动失败；{@code keyword} 强制关键词。演示语料三条，与工作区 {@code KNOWLEDGE.md} 互补。
     */
    @Bean
    RetrievePort retrievePort(DreamScopeProperties props, Environment env) {
        if (useSimpleRag(props, env)) {
            SimpleKnowledgeRetrievePort port = SimpleKnowledgeRetrievePort.dashScope(env.getProperty("DASHSCOPE_API_KEY"));
            seedSimple(port);
            return port;
        }
        InMemoryKeywordIndex index = new InMemoryKeywordIndex();
        seedKeyword(index);
        return index;
    }

    private static boolean useSimpleRag(DreamScopeProperties props, Environment env) {
        String provider = props.getRag().getProvider();
        String key = env.getProperty("DASHSCOPE_API_KEY");
        boolean hasKey = key != null && !key.isBlank();
        if (provider == null || provider.isBlank() || "auto".equalsIgnoreCase(provider)) {
            return hasKey;
        }
        if ("simple".equalsIgnoreCase(provider)) {
            if (!hasKey) {
                throw new IllegalStateException("dream-scope.rag.provider=simple requires DASHSCOPE_API_KEY");
            }
            return true;
        }
        if ("keyword".equalsIgnoreCase(provider)) {
            return false;
        }
        throw new IllegalStateException("unknown dream-scope.rag.provider: " + provider);
    }

    private static void seedKeyword(InMemoryKeywordIndex index) {
        for (String[] row : DEMO_CHUNKS) {
            index.ingest(row[0], row[1], row[2]);
        }
    }

    private static void seedSimple(SimpleKnowledgeRetrievePort port) {
        for (String[] row : DEMO_CHUNKS) {
            port.addText(row[0], row[1], row[2]);
        }
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

    private static final String[][] DEMO_CHUNKS = {
        {"dream-scope 是基于 AgentScope 2.0 的模块化单体 Agent 运行时。", "intro", "intro"},
        {"对外 HTTP：POST /api/agents/invoke 同步调用，POST /api/agents/stream 为 SSE。默认 agentId 为 chat。", "http", "intro"},
        {"生产会话走 Redis。请求同时带 sessionId 与 userId 时按该二元组续聊。", "redis", "intro"}
    };
}

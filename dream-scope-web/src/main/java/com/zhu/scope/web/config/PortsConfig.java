package com.zhu.scope.web.config;

import com.zhu.scope.adapter.ChatHarnessOptions;
import com.zhu.scope.adapter.ScopeA2aClientAgent;
import com.zhu.scope.adapter.ScopeChatAgent;
import com.zhu.scope.adapter.ScopeKnowledgeAgent;
import com.zhu.scope.adapter.nacos.ChatNacosClient;
import com.zhu.scope.adapter.nacos.ChatNacosSettings;
import com.zhu.scope.adapter.a2a.ScopeA2aServer;
import com.zhu.scope.adapter.mcp.ChatMcpServer;
import com.zhu.scope.adapter.rag.ChatEmbeddingSettings;
import com.zhu.scope.adapter.rag.ChatPgVectorSettings;
import com.zhu.scope.adapter.rag.DashScopeQueryRewritePort;
import com.zhu.scope.adapter.rag.DashScopeRerankPort;
import com.zhu.scope.adapter.rag.SimpleKnowledgeRetrievePort;
import com.zhu.scope.agent.AgentHandler;
import com.zhu.scope.agent.AgentRegistry;
import com.zhu.scope.agent.InMemoryAgentRegistry;
import com.zhu.scope.knowledge.KnowledgeIngestTracker;
import com.zhu.scope.knowledge.QueryRewritePort;
import com.zhu.scope.knowledge.RerankPort;
import com.zhu.scope.knowledge.RetrievePort;
import com.zhu.scope.rag.AdvancedRetrievePort;
import com.zhu.scope.rag.HybridRetrievePort;
import com.zhu.scope.rag.InMemoryKeywordIndex;
import com.zhu.scope.rag.LexicalRerankPort;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.zhu.scope.web.util.WorkspaceSubagentSeed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * web 组合根：把 {@code dream-scope.*} 收成 domain {@link AgentHandler}，再交给 {@link AgentRegistry}。
 *
 * <p>本类是产品入口（8091）唯一允许「知道 adapter」的地方。Controller 只认 {@link AgentHandler} /
 * {@link AgentRegistry}，不碰 {@code io.agentscope}。官方 starter 在 {@code dream-scope-boot}，不经过这里。
 *
 * <h2>装配顺序（chat）</h2>
 *
 * <pre>
 * DreamScopeProperties
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
 * <p>模型 Key 走 {@code dream-scope.model.api-key}（及各家 {@code *-api-key}），不直接读环境变量。
 */
@Configuration
@EnableConfigurationProperties(DreamScopeProperties.class)
public class PortsConfig {

    private static final Logger log = LoggerFactory.getLogger(PortsConfig.class);

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
    AgentHandler chatAgentHandler(DreamScopeProperties props, RetrievePort retrievePort, ObjectProvider<ChatNacosClient> nacos) {
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
        log.info(
                "chat bean assemble model={} fallback={} hasApiKey={} workspace={} timeout={} planMode={} nacos={} mcpServers={}",
                modelId,
                fallbackId,
                apiKey != null && !apiKey.isBlank(),
                props.getWorkspaceDir(),
                props.getChatTimeout(),
                props.getPlanMode().isEnabled(),
                nacosClient != null,
                mcpServers(props).size());
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
        return ScopeChatAgent.create(modelId, apiKey, options, retrievePort, mcpServers(props), nacosClient);
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "dream-scope.nacos", name = "enabled", havingValue = "true")
    ChatNacosClient chatNacosClient(DreamScopeProperties props) {
        log.info(
                "nacos ai bean open serverAddr={} namespace={} prompt={} a2aRegistry={} a2aDiscovery={} skill={}",
                props.getNacos().getServerAddr(),
                props.getNacos().getNamespace(),
                props.getNacos().getPrompt().isEnabled(),
                props.getNacos().getA2a().isRegistryEnabled(),
                props.getNacos().getA2a().isDiscoveryEnabled(),
                props.getNacos().getSkill().isEnabled());
        return ChatNacosClient.open(nacosSettings(props));
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
            @Qualifier("chatAgentHandler") AgentHandler chatAgentHandler,
            DreamScopeProperties props,
            ObjectProvider<ChatNacosClient> nacos) {
        ChatNacosClient client = nacos.getIfAvailable();
        boolean register = client != null && client.a2aRegistryEnabled();
        log.info("a2a server create publicUrl={} nacosRegistry={}", props.getA2a().getPublicUrl(), register);
        return ScopeA2aServer.create(chatAgentHandler, props.getA2a().getPublicUrl(), client);
    }

    @Bean
    ApplicationListener<ApplicationReadyEvent> a2aPostEndpointReady(
            ScopeA2aServer server, DreamScopeProperties props) {
        return event -> {
            if (props.getA2a().isEnabled()) {
                log.info("a2a postEndpointReady publicUrl={}", props.getA2a().getPublicUrl());
                server.postEndpointReady();
            }
        };
    }

    /**
     * 可选 {@code a2a} 客户端。Nacos 发现优先；否则 well-known {@code remote-url}。
     */
    @Bean(name = "a2aAgentHandler")
    @ConditionalOnBean(ChatNacosClient.class)
    @ConditionalOnProperty(prefix = "dream-scope.nacos.a2a", name = "discovery-enabled", havingValue = "true")
    @ConditionalOnMissingBean(name = "a2aAgentHandler")
    AgentHandler a2aNacosAgentHandler(ChatNacosClient nacos, DreamScopeProperties props) {
        String name = props.getNacos().getA2a().getDiscoveryAgentName();
        log.info("a2a client from nacos agent={}", name);
        return ScopeA2aClientAgent.fromNacos(nacos, name);
    }

    @Bean(name = "a2aAgentHandler")
    @ConditionalOnProperty(prefix = "dream-scope.a2a", name = "remote-url")
    @ConditionalOnMissingBean(name = "a2aAgentHandler")
    AgentHandler a2aAgentHandler(DreamScopeProperties props) {
        log.info("a2a client from well-known remoteUrl={}", props.getA2a().getRemoteUrl());
        return new ScopeA2aClientAgent(props.getA2a().getRemoteUrl());
    }

    /**
     * 按 {@link AgentHandler#id()} 注册。Spring 会注入容器里所有 Handler（chat 必有，knowledge 必有，a2a 视配置）。
     * {@code AgentInvokeController} 只通过本 Bean 查找，不直接依赖具体实现类。
     */
    @Bean
    AgentRegistry agentRegistry(List<AgentHandler> handlers) {
        List<String> ids = handlers.stream().map(AgentHandler::id).toList();
        log.info("agent registry ids={}", ids);
        return new InMemoryAgentRegistry(handlers);
    }

    /**
     * chat 的 {@code retrieve} 与 {@code knowledge} Handler 共用。换实现只换这个 Bean。
     *
     * <p>{@code dream-scope.rag.provider=auto}（默认）：配了 {@code rag.pg.jdbc-url} 且有模型 Key 走官方
     * {@link SimpleKnowledgeRetrievePort} + {@code PgVectorStore}；只有 Key 则内存 {@code InMemoryStore}；
     * 无 Key、PG 连不上、或 DashScope embedding 额度不足时回退关键词，避免演示入库把进程打挂。
     * {@code keyword} 强制关键词。embedding 模型看 {@code dream-scope.rag.embedding-model}，
     * 与对话 {@code model.default} 分开。演示语料三条，pgvector 用稳定 id upsert。
     */
    @Bean(destroyMethod = "close")
    RetrievePort retrievePort(DreamScopeProperties props) {
        return createRetrievePort(
                props,
                key -> SimpleKnowledgeRetrievePort.dashScope(
                        key,
                        embeddingSettings(props),
                        props.getRag().getScoreThreshold(),
                        props.getRag().getChunkSize(),
                        props.getRag().getChunkOverlap()),
                PortsConfig::openPg);
    }

    @Bean
    KnowledgeIngestTracker knowledgeIngestTracker() {
        return new KnowledgeIngestTracker();
    }

    public static RetrievePort createRetrievePort(
            DreamScopeProperties props, Function<String, SimpleKnowledgeRetrievePort> simpleFactory) {
        return createRetrievePort(props, simpleFactory, PortsConfig::openPg);
    }

    public static RetrievePort createRetrievePort(
            DreamScopeProperties props,
            Function<String, SimpleKnowledgeRetrievePort> simpleFactory,
            Function<DreamScopeProperties, SimpleKnowledgeRetrievePort> pgFactory) {
        if (usePgRag(props)) {
            SimpleKnowledgeRetrievePort port = null;
            try {
                port = pgFactory.apply(props);
                HybridRetrievePort hybrid = wrapHybrid(port);
                seedIfEmpty(port);
                log.info(
                        "rag port=pgVector jdbc={} schema={} table={} embedding={} dims={}",
                        props.getRag().getPg().getJdbcUrl(),
                        props.getRag().getPg().getSchema(),
                        props.getRag().getPg().getTable(),
                        embeddingSettings(props).modelName(),
                        embeddingSettings(props).dimensions());
                return enhance(hybrid, props);
            } catch (RuntimeException ex) {
                closeQuietly(port);
                if (isEmbeddingFailure(ex)) {
                    log.warn("DashScope embedding 额度不足，RAG 回退关键词: {}", rootMessage(ex));
                    return enhance(keywordPort(), props);
                }
                if (!isAutoRag(props)) {
                    throw ex;
                }
                log.warn("pgvector 不可用，auto RAG 回退: {}", rootMessage(ex));
            }
        }
        if (useSimpleRag(props)) {
            SimpleKnowledgeRetrievePort port = null;
            try {
                port = simpleFactory.apply(props.getModel().apiKeyFor("dashscope:embedding"));
                HybridRetrievePort hybrid = wrapHybrid(port);
                seedIfEmpty(port);
                log.info(
                        "rag port=simpleKnowledge embedding={} dims={} chunks={}",
                        embeddingSettings(props).modelName(),
                        embeddingSettings(props).dimensions(),
                        DEMO_CHUNKS.length);
                return enhance(hybrid, props);
            } catch (RuntimeException ex) {
                closeQuietly(port);
                if (isEmbeddingFailure(ex)) {
                    log.warn("DashScope embedding 额度不足，RAG 回退关键词: {}", rootMessage(ex));
                    return enhance(keywordPort(), props);
                }
                if (!isAutoRag(props)) {
                    throw ex;
                }
                log.warn("DashScope embedding 入库失败，auto RAG 回退关键词索引: {}", rootMessage(ex));
            }
        }
        return enhance(keywordPort(), props);
    }

    public static RetrievePort enhance(RetrievePort port, DreamScopeProperties props) {
        if (port == null) {
            return null;
        }
        DreamScopeProperties.RagSettings rag = props.getRag();
        RerankPort rerank = null;
        QueryRewritePort rewrite = null;
        String key = props.getModel().apiKeyFor("dashscope:embedding");
        if (rag.isRerankEnabled()) {
            if (key != null && !key.isBlank()) {
                try {
                    rerank = new DashScopeRerankPort(key, rag.getRerankModel());
                    log.info("rag rerank=dashscope model={}", rag.getRerankModel());
                } catch (RuntimeException ex) {
                    log.warn("rag rerank dashscope unavailable, lexical fallback: {}", ex.getMessage());
                    rerank = new LexicalRerankPort();
                }
            } else {
                rerank = new LexicalRerankPort();
                log.info("rag rerank=lexical");
            }
        }
        if (rag.isRewriteEnabled() && key != null && !key.isBlank()) {
            String rewriteModel = rag.getRewriteModel();
            if (rewriteModel == null || rewriteModel.isBlank()) {
                rewriteModel = props.getModel().getChat();
                if (rewriteModel == null || rewriteModel.isBlank()) {
                    rewriteModel = props.getModel().getDefault();
                }
            }
            try {
                rewrite = new DashScopeQueryRewritePort(key, rewriteModel);
                log.info("rag rewrite=dashscope model={}", rewriteModel);
            } catch (RuntimeException ex) {
                log.warn("rag rewrite unavailable: {}", ex.getMessage());
            }
        }
        if (rerank == null && rewrite == null) {
            return port;
        }
        return new AdvancedRetrievePort(port, rewrite, rerank, rag.getCandidateMultiplier());
    }

    private static void seedIfEmpty(SimpleKnowledgeRetrievePort port) {
        if (port.hasStoredDocuments()) {
            int restored = port.rebuildSourceIndexFromStore();
            log.info(
                    "rag skip seed, store already has documents restoredChunks={} sources={}",
                    restored,
                    port.sources().size());
            return;
        }
        seedSimple(port);
    }

    private static HybridRetrievePort wrapHybrid(SimpleKnowledgeRetrievePort port) {
        HybridRetrievePort hybrid = new HybridRetrievePort(port);
        port.mirrorKeyword(hybrid.keywordIndex());
        return hybrid;
    }

    private static RetrievePort keywordPort() {
        InMemoryKeywordIndex index = new InMemoryKeywordIndex();
        seedKeyword(index);
        log.info("rag port=keywordIndex chunks={}", DEMO_CHUNKS.length);
        return index;
    }

    private static void closeQuietly(RetrievePort port) {
        if (port == null) {
            return;
        }
        try {
            port.close();
        } catch (RuntimeException ignored) {
            // 回退关键词时关掉半开的 pg 连接
        }
    }

    public static boolean isEmbeddingFailure(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String msg = current.getMessage() == null ? "" : current.getMessage();
            String type = current.getClass().getName();
            if (msg.contains("Free quota")
                    || msg.contains("FreeTierOnly")
                    || msg.contains("AllocationQuota")
                    || type.contains("EmbeddingException")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage();
    }

    private static boolean isAutoRag(DreamScopeProperties props) {
        String provider = props.getRag().getProvider();
        return provider == null || provider.isBlank() || "auto".equalsIgnoreCase(provider);
    }

    private static boolean useSimpleRag(DreamScopeProperties props) {
        String provider = props.getRag().getProvider();
        String key = props.getModel().apiKeyFor("dashscope:embedding");
        boolean hasKey = key != null && !key.isBlank();
        if (provider == null || provider.isBlank() || "auto".equalsIgnoreCase(provider)) {
            return hasKey;
        }
        if ("simple".equalsIgnoreCase(provider)) {
            if (!hasKey) {
                throw new IllegalStateException("dream-scope.rag.provider=simple requires dream-scope.model.api-key");
            }
            return true;
        }
        if ("keyword".equalsIgnoreCase(provider)) {
            return false;
        }
        if (isPgProvider(provider)) {
            return false;
        }
        throw new IllegalStateException("unknown dream-scope.rag.provider: " + provider);
    }

    private static boolean usePgRag(DreamScopeProperties props) {
        String provider = props.getRag().getProvider();
        boolean hasKey = hasEmbeddingKey(props);
        boolean hasJdbc = pgSettings(props).configured();
        if (isPgProvider(provider)) {
            if (!hasJdbc) {
                throw new IllegalStateException("dream-scope.rag.provider=pg requires dream-scope.rag.pg.jdbc-url");
            }
            if (!hasKey) {
                throw new IllegalStateException("dream-scope.rag.provider=pg requires dream-scope.model.api-key");
            }
            return true;
        }
        return isAutoRag(props) && hasJdbc && hasKey;
    }

    private static boolean isPgProvider(String provider) {
        return "pg".equalsIgnoreCase(provider) || "pgvector".equalsIgnoreCase(provider);
    }

    private static boolean hasEmbeddingKey(DreamScopeProperties props) {
        String key = props.getModel().apiKeyFor("dashscope:embedding");
        return key != null && !key.isBlank();
    }

    private static SimpleKnowledgeRetrievePort openPg(DreamScopeProperties props) {
        return SimpleKnowledgeRetrievePort.dashScopePg(
                props.getModel().apiKeyFor("dashscope:embedding"),
                pgSettings(props),
                embeddingSettings(props),
                props.getRag().getScoreThreshold(),
                props.getRag().getChunkSize(),
                props.getRag().getChunkOverlap());
    }

    public static ChatEmbeddingSettings embeddingSettings(DreamScopeProperties props) {
        DreamScopeProperties.RagSettings rag = props.getRag();
        return new ChatEmbeddingSettings(rag.getEmbeddingModel(), rag.getEmbeddingDimensions());
    }

    static ChatPgVectorSettings pgSettings(DreamScopeProperties props) {
        DreamScopeProperties.PgSettings pg = props.getRag().getPg();
        return new ChatPgVectorSettings(
                pg.getJdbcUrl(), pg.getUsername(), pg.getPassword(), pg.getSchema(), pg.getTable());
    }

    private static void seedKeyword(InMemoryKeywordIndex index) {
        for (String[] row : DEMO_CHUNKS) {
            index.ingest(row[0], row[1], row[2]);
        }
    }

    private static void seedSimple(SimpleKnowledgeRetrievePort port) {
        for (String[] row : DEMO_CHUNKS) {
            port.addText("demo-" + row[1], row[0], row[1], row[2]);
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

    public static ChatNacosSettings nacosSettings(DreamScopeProperties props) {
        DreamScopeProperties.NacosSettings nacos = props.getNacos();
        DreamScopeProperties.PromptSettings prompt = nacos.getPrompt();
        DreamScopeProperties.A2aNacosSettings a2a = nacos.getA2a();
        DreamScopeProperties.SkillSettings skill = nacos.getSkill();
        return new ChatNacosSettings(
                nacos.getServerAddr(),
                nacos.getNamespace(),
                nacos.getUsername(),
                nacos.getPassword(),
                prompt.isEnabled(),
                prompt.getSysPromptKey(),
                prompt.getVersion(),
                prompt.getLabel(),
                prompt.getVariables(),
                a2a.isRegistryEnabled(),
                a2a.isDiscoveryEnabled(),
                a2a.getDiscoveryAgentName(),
                a2a.isRegisterAsLatest(),
                a2a.isRegisterEndpoint(),
                a2a.isStreaming(),
                skill.isEnabled(),
                skill.getNames(),
                skill.getVersion(),
                skill.getLabel());
    }

    private static final String[][] DEMO_CHUNKS = {
        {"dream-scope 是基于 AgentScope 2.0 的模块化单体 Agent 运行时。", "intro", "intro"},
        {"对外 HTTP：POST /api/agents/invoke 同步调用，POST /api/agents/stream 为 SSE。默认 agentId 为 chat。", "http", "intro"},
        {"生产会话走 Redis。请求同时带 sessionId 与 userId 时按该二元组续聊。", "redis", "intro"}
    };
}

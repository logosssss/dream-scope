package com.zhu.scope.web.config;

import com.zhu.scope.adapter.rag.ChatEmbeddingSettings;
import com.zhu.scope.adapter.rag.ChatPgVectorSettings;
import com.zhu.scope.adapter.rag.DashScopeQueryRewritePort;
import com.zhu.scope.adapter.rag.DashScopeRerankPort;
import com.zhu.scope.adapter.rag.SimpleKnowledgeRetrievePort;
import com.zhu.scope.knowledge.KnowledgeIngestTracker;
import com.zhu.scope.knowledge.QueryRewritePort;
import com.zhu.scope.knowledge.RerankPort;
import com.zhu.scope.knowledge.RetrievePort;
import com.zhu.scope.rag.AdvancedRetrievePort;
import com.zhu.scope.rag.HybridRetrievePort;
import com.zhu.scope.rag.InMemoryKeywordIndex;
import com.zhu.scope.rag.LexicalRerankPort;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 检索端口。{@code auto}：有 jdbc 且有 Key 走 pgvector；只有 Key 走内存向量；失败回退关键词。
 * 成功后包一层混合检索，再按配置叠加精排 / 问句改写。
 */
@Configuration
public class RagPortsConfig {

    private static final Logger log = LoggerFactory.getLogger(RagPortsConfig.class);

    private static final String[][] DEMO_CHUNKS = {
        {"dream-scope 是基于 AgentScope 2.0 的模块化单体 Agent 运行时。", "intro", "intro"},
        {"对外 HTTP：POST /api/agents/invoke 同步调用，POST /api/agents/stream 为 SSE。默认 agentId 为 chat。", "http", "intro"},
        {"生产会话走 Redis。请求同时带 sessionId 与 userId 时按该二元组续聊。", "redis", "intro"}
    };

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
                RagPortsConfig::openPg);
    }

    @Bean
    KnowledgeIngestTracker knowledgeIngestTracker() {
        return new KnowledgeIngestTracker();
    }

    public static RetrievePort createRetrievePort(
            DreamScopeProperties props, Function<String, SimpleKnowledgeRetrievePort> simpleFactory) {
        return createRetrievePort(props, simpleFactory, RagPortsConfig::openPg);
    }

    public static RetrievePort createRetrievePort(
            DreamScopeProperties props,
            Function<String, SimpleKnowledgeRetrievePort> simpleFactory,
            Function<DreamScopeProperties, SimpleKnowledgeRetrievePort> pgFactory) {
        if (usePgRag(props)) {
            SimpleKnowledgeRetrievePort port = null;
            try {
                port = pgFactory.apply(props);
                HybridRetrievePort hybrid = wrapHybrid(port, props);
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
                    return enhance(keywordPort(props), props);
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
                HybridRetrievePort hybrid = wrapHybrid(port, props);
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
                    return enhance(keywordPort(props), props);
                }
                if (!isAutoRag(props)) {
                    throw ex;
                }
                log.warn("DashScope embedding 入库失败，auto RAG 回退关键词索引: {}", rootMessage(ex));
            }
        }
        return enhance(keywordPort(props), props);
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

    public static ChatEmbeddingSettings embeddingSettings(DreamScopeProperties props) {
        DreamScopeProperties.RagSettings rag = props.getRag();
        return new ChatEmbeddingSettings(rag.getEmbeddingModel(), rag.getEmbeddingDimensions());
    }

    static ChatPgVectorSettings pgSettings(DreamScopeProperties props) {
        DreamScopeProperties.PgSettings pg = props.getRag().getPg();
        return new ChatPgVectorSettings(
                pg.getJdbcUrl(), pg.getUsername(), pg.getPassword(), pg.getSchema(), pg.getTable());
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

    private static HybridRetrievePort wrapHybrid(SimpleKnowledgeRetrievePort port, DreamScopeProperties props) {
        int size = props.getRag().getChunkSize();
        int overlap = props.getRag().getChunkOverlap();
        InMemoryKeywordIndex keyword = new InMemoryKeywordIndex(0.0, size, overlap);
        HybridRetrievePort hybrid = new HybridRetrievePort(port, keyword);
        port.mirrorKeyword(keyword);
        hybrid.setFileChunkReader(
                (name, content) -> SimpleKnowledgeRetrievePort.readPlainTexts(name, content, size, overlap));
        return hybrid;
    }

    private static RetrievePort keywordPort(DreamScopeProperties props) {
        InMemoryKeywordIndex index =
                new InMemoryKeywordIndex(0.0, props.getRag().getChunkSize(), props.getRag().getChunkOverlap());
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
}

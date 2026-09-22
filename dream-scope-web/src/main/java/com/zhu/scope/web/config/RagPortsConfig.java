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
 * RAG 组合根：按配置装配唯一的 {@link RetrievePort} Bean，供 HTTP 知识接口与 chat {@code retrieve} 工具注入。
 *
 * <h2>在整条链路里的位置</h2>
 *
 * <pre>
 * DreamScopeProperties.rag.*
 *   → 本类 createRetrievePort / enhance
 *     → SimpleKnowledgeRetrievePort（向量）± HybridRetrievePort（关键词 RRF）
 *       → AdvancedRetrievePort（可选改写 + 精排）
 *         → KnowledgeController / ChatTools.retrieve
 * </pre>
 *
 * <p>从 {@code PortsConfig} 拆出，避免聊天 / Nacos / A2A / RAG 挤在一个配置类。静态工厂给单测用，
 * 不必起完整 Spring 上下文。
 *
 * <h2>provider 选择（{@code dream-scope.rag.provider}）</h2>
 *
 * <ul>
 *   <li>{@code auto}（默认）：有 jdbc + embedding Key → pg；仅有 Key → 内存向量；否则或失败 → 关键词。
 *   <li>{@code pg} / {@code pgvector}：强制 pg，缺 jdbc 或 Key 直接抛错，不静默降级。
 *   <li>{@code simple}：强制内存向量，缺 Key 抛错。
 *   <li>{@code keyword}：进程内关键词，不调 embedding。
 * </ul>
 *
 * <h2>包装顺序（成功路径）</h2>
 *
 * <ol>
 *   <li>打开向量端口（pg 或 simple）。
     *   <li>{@link #wrapHybrid}：Hybrid 持有关键词索引。向量写入成功后由 Hybrid 镜像，失败也由 Hybrid 降级；
     *       切块参数与向量 Reader 共用配置，避免两侧块边界对不齐。
 *   <li>{@link #seedIfEmpty}：库空则灌演示语料；pg 已有数据则重建 source 索引并镜像关键词。
 *   <li>{@link #enhance}：按开关挂 DashScope / 词项精排与可选问句改写。
 * </ol>
 *
 * <h2>失败回退</h2>
 *
 * <ul>
 *   <li>embedding 额度类错误（FreeTierOnly 等）→ 关半开端口 → 关键词索引 + 仍走 {@link #enhance}。
 *   <li>{@code auto} 下其它 pg / 内存失败 → 日志后继续下一档；显式 {@code pg}/{@code simple} 则上抛。
 * </ul>
 *
 * <h2>刻意不做的事</h2>
 *
 * <ul>
 *   <li>不在这里写检索算法；RRF / 精排 / 改写在 knowledge / adapter。
 *   <li>不挂 {@code ReActAgent.knowledge()}，避免与工具检索双通道。
 * </ul>
 */
@Configuration
public class RagPortsConfig {

    private static final Logger log = LoggerFactory.getLogger(RagPortsConfig.class);

    /**
     * 空库演示语料：{@code {正文, source, docType}}。向量与关键词回退共用，保证本地无 Key / 无 pg 也能检索到内容。
     */
    private static final String[][] DEMO_CHUNKS = {
        {"dream-scope 是基于 AgentScope 2.0 的模块化单体 Agent 运行时。", "intro", "intro"},
        {"对外 HTTP：POST /api/agents/invoke 同步调用，POST /api/agents/stream 为 SSE。默认 agentId 为 chat。", "http", "intro"},
        {"生产会话走 Redis。请求同时带 sessionId 与 userId 时按该二元组续聊。", "redis", "intro"}
    };

    /**
     * 进程内唯一检索端口。{@code destroyMethod = "close"} 在停机时关掉 pg 连接等资源。
     *
     * <p>Bean 路径固定走 DashScope embedding 工厂；单测可调 {@link #createRetrievePort} 注入假工厂。
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
                RagPortsConfig::openPg);
    }

    /** 异步入库 Job 状态（HTTP {@code GET /api/knowledge/jobs/{id}}）。与检索端口生命周期无关。 */
    @Bean
    KnowledgeIngestTracker knowledgeIngestTracker() {
        return new KnowledgeIngestTracker();
    }

    /**
     * 单测入口：只替换内存向量工厂，pg 仍用默认 {@link #openPg}。
     */
    public static RetrievePort createRetrievePort(DreamScopeProperties props, Function<String, SimpleKnowledgeRetrievePort> simpleFactory) {
        return createRetrievePort(props, simpleFactory, RagPortsConfig::openPg);
    }

    /**
     * 按 provider 装配检索栈。顺序：pg → simple → keyword；每一档成功后都经 {@link #enhance}。
     *
     * @param simpleFactory 入参为 embedding API Key，产出内存向量端口
     * @param pgFactory 入参为完整配置，产出 pg 向量端口（单测可注入假实现）
     */
    public static RetrievePort createRetrievePort(
            DreamScopeProperties props,
            Function<String, SimpleKnowledgeRetrievePort> simpleFactory,
            Function<DreamScopeProperties, SimpleKnowledgeRetrievePort> pgFactory) {
        if (usePgRag(props)) {
            SimpleKnowledgeRetrievePort port = null;
            try {
                port = pgFactory.apply(props);
                HybridRetrievePort hybrid = wrapHybrid(port, props);
                seedIfEmpty(hybrid, port);
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
                seedIfEmpty(hybrid, port);
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

    /**
     * 在已有检索端口外叠加精排 / 问句改写。二者都关则原样返回。
     *
     * <ul>
     *   <li>精排开且有 Key → {@link DashScopeRerankPort}；构造失败或无 Key → {@link LexicalRerankPort}。
     *   <li>改写开且有 Key → {@link DashScopeQueryRewritePort}；模型空则退对话 chat / default。
     *   <li>最终包 {@link AdvancedRetrievePort}，候选倍数取自 {@code candidateMultiplier}。
     * </ul>
     */
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

    /**
     * 是否为 DashScope embedding 额度 / 配额类失败。用于决定「立刻关键词回退」还是「auto 下一档 / 显式 provider 上抛」。
     */
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

    /** 从配置抽出 embedding 模型名与维度，交给 adapter 工厂。 */
    public static ChatEmbeddingSettings embeddingSettings(DreamScopeProperties props) {
        DreamScopeProperties.RagSettings rag = props.getRag();
        return new ChatEmbeddingSettings(rag.getEmbeddingModel(), rag.getEmbeddingDimensions());
    }

    /** pg 连接与表名。密码只在打开连接时使用，不打日志。 */
    static ChatPgVectorSettings pgSettings(DreamScopeProperties props) {
        DreamScopeProperties.PgSettings pg = props.getRag().getPg();
        return new ChatPgVectorSettings(
                pg.getJdbcUrl(), pg.getUsername(), pg.getPassword(), pg.getSchema(), pg.getTable());
    }

    /**
     * 空库经 Hybrid 灌演示语料（向量成功后自动镜像关键词）。已有文档则跳过 seed，
     * 从 pg 重建 source 索引，再由 Hybrid 回填关键词。内存库 {@code hasStoredDocuments} 恒为 false。
     */
    private static void seedIfEmpty(HybridRetrievePort hybrid, SimpleKnowledgeRetrievePort port) {
        if (port.hasStoredDocuments()) {
            int restored = port.rebuildSourceIndexFromStore();
            int mirrored = hybrid.refillKeyword(port.storedChunks());
            log.info(
                    "rag skip seed, store already has documents restoredChunks={} mirrored={} sources={}",
                    restored,
                    mirrored,
                    port.sources().size());
            return;
        }
        for (String[] row : DEMO_CHUNKS) {
            hybrid.addText("demo-" + row[1], row[0], row[1], row[2]);
        }
    }

    /**
     * 向量端口外包混合检索。关键词索引只挂在 Hybrid 上；PDF 等 embedding 失败时用同一套 Reader 抽正文。
     */
    private static HybridRetrievePort wrapHybrid(SimpleKnowledgeRetrievePort port, DreamScopeProperties props) {
        int size = props.getRag().getChunkSize();
        int overlap = props.getRag().getChunkOverlap();
        InMemoryKeywordIndex keyword = new InMemoryKeywordIndex(0.0, size, overlap);
        HybridRetrievePort hybrid = new HybridRetrievePort(port, keyword);
        hybrid.setFileChunkReader(
                (name, content) -> SimpleKnowledgeRetrievePort.readPlainTexts(name, content, size, overlap));
        return hybrid;
    }

    /** 纯关键词端口（无向量）。仍 seed 演示语料，本地无 Key 也能演示检索。 */
    private static RetrievePort keywordPort(DreamScopeProperties props) {
        InMemoryKeywordIndex index =
                new InMemoryKeywordIndex(0.0, props.getRag().getChunkSize(), props.getRag().getChunkOverlap());
        seedKeyword(index);
        log.info("rag port=keywordIndex chunks={}", DEMO_CHUNKS.length);
        return index;
    }

    /** 回退时关掉半开的 pg / 向量资源，避免泄漏连接。关闭异常吞掉。 */
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

    /** 取因果链最内层 message，日志更短。 */
    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage();
    }

    /** provider 为空或 {@code auto} 时允许静默降级。 */
    private static boolean isAutoRag(DreamScopeProperties props) {
        String provider = props.getRag().getProvider();
        return provider == null || provider.isBlank() || "auto".equalsIgnoreCase(provider);
    }

    /**
     * 是否尝试内存向量档。{@code auto} 有 Key 则试；{@code simple} 强制且缺 Key 抛错；
     * {@code keyword}/{@code pg} 跳过本档。
     */
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

    /**
     * 是否尝试 pg 档。显式 {@code pg}/{@code pgvector} 缺 jdbc 或 Key 抛错；
     * {@code auto} 仅在同时具备 jdbc 与 Key 时尝试。
     */
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

    /** 默认 pg 工厂：DashScope embedding + PgVectorStore，切块 / 阈值来自配置。 */
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

}

package com.zhu.scope.adapter.rag;

import com.zhu.scope.adapter.LogText;
import com.zhu.scope.knowledge.EmbeddingIngestException;
import com.zhu.scope.knowledge.IngestedChunk;
import com.zhu.scope.knowledge.IngestProgressListener;
import com.zhu.scope.knowledge.KnowledgeSource;
import com.zhu.scope.knowledge.RetrieveHit;
import com.zhu.scope.knowledge.RetrievePort;
import com.zhu.scope.rag.KnowledgeTextFile;
import io.agentscope.core.embedding.EmbeddingModel;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.embedding.dashscope.DashScopeTextEmbedding;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.rag.knowledge.SimpleKnowledge;
import io.agentscope.core.rag.model.Document;
import io.agentscope.core.rag.model.DocumentMetadata;
import io.agentscope.core.rag.model.RetrieveConfig;
import io.agentscope.core.rag.store.InMemoryStore;
import io.agentscope.core.rag.store.PgVectorStore;
import io.agentscope.core.rag.store.VDBStoreBase;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AgentScope 官方 Simple Knowledge 的适配器：把 {@link SimpleKnowledge} + 向量库收成 domain 的
 * {@link RetrievePort}，供 chat 工具 {@code retrieve}、{@code knowledge} Agent、HTTP 知识接口共用。
 *
 * <h2>在整条链路里的位置</h2>
 *
 * <pre>
 * HTTP / ChatTools.retrieve
 *   → RetrievePort（常再包 Hybrid + Advanced）
 *     → 本类 SimpleKnowledgeRetrievePort
 *       → SimpleKnowledge（embed + search）
 *       → InMemoryStore 或 PgVectorStore
 * </pre>
 *
 * <p>{@code io.agentscope} 只允许出现在 adapter；domain / knowledge / web Controller 看不到本类。
 *
 * <h2>刻意不做的事</h2>
 *
 * <ul>
 *   <li>不挂 {@code ReActAgent.knowledge()} / {@code RAGMode.AGENTIC}，避免与 {@code ChatTools.retrieve}
 *       各搜一次、引用两套。
 *   <li>不在本类做 RRF / 精排 / 问句改写；那些在 {@code HybridRetrievePort}、{@code AdvancedRetrievePort}。
 * </ul>
 *
 * <h2>来源索引与关键词镜像</h2>
 *
 * <ul>
 *   <li>{@link #sourceIds}：进程内 {@code source → docId[]}，支撑列表、按源覆盖删除。内存库随进程清空；
 *       pg 重启后靠 {@link #rebuildSourceIndexFromStore()} 从 payload 重建，正文经 {@link #storedChunks()}
 *       交给 Hybrid 回填关键词。本类不持有关键词索引。
 * </ul>
 *
 * @see <a href="https://java.agentscope.io/v2/en/integration/rag/simple.html">Simple Knowledge</a>
 */
@SuppressWarnings({"deprecation", "removal"})
public final class SimpleKnowledgeRetrievePort implements RetrievePort {

    private static final Logger log = LoggerFactory.getLogger(SimpleKnowledgeRetrievePort.class);

    public static final String DEFAULT_EMBEDDING_MODEL = ChatEmbeddingSettings.DEFAULT_MODEL;

    public static final int DEFAULT_DIMENSIONS = ChatEmbeddingSettings.DEFAULT_DIMENSIONS;

    /** 单次 embed / 检索 / 按 id 删除的阻塞上限。大文件分批入库另用 2 分钟。 */
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    /** AgentScope 封装：写库时 embed，查库时 query embed + 相似度检索。 */
    private final SimpleKnowledge knowledge;

    /**
     * 需要关闭的底层资源（多为 {@link PgVectorStore}）。内存库一般不关；{@link #close()} 只关这一份。
     */
    private final AutoCloseable store;

    /**
     * 向量库句柄。按 docId 删除、判断是否 pg 都走它；与 {@link #knowledge} 共用同一 store 实例。
     */
    private final VDBStoreBase embeddingStore;

    /** 检索最低相似度，写入 {@link RetrieveConfig}，已钳制到 [0, 1]。 */
    private final double scoreThreshold;

    /** Reader 切块大小 / 重叠，仅 {@link #addFile} 使用；{@link #addText} 不切块。 */
    private final int chunkSize;

    private final int chunkOverlap;

    /**
     * 来源 → 该源下所有块 id。同名覆盖先 {@link #deleteBySource} 再写；重启后 pg 需
     * {@link #rebuildSourceIndexFromStore()}。
     */
    private final Map<String, List<String>> sourceIds = new ConcurrentHashMap<>();

    /** {@link #rebuildSourceIndexFromStore()} 导出的正文，供 Hybrid 回填关键词。 */
    private List<IngestedChunk> exported = List.of();

    /**
     * 大文件入库进度。按调用线程保存，并发入库互不覆盖；传 null 表示本线程结束。
     */
    private static final ThreadLocal<IngestProgressListener> PROGRESS = new ThreadLocal<>();

    public SimpleKnowledgeRetrievePort(SimpleKnowledge knowledge) {
        this(knowledge, null, null, 0.0, ChatKnowledgeReaders.CHUNK_SIZE, ChatKnowledgeReaders.OVERLAP);
    }

    SimpleKnowledgeRetrievePort(SimpleKnowledge knowledge, AutoCloseable store) {
        this(knowledge, store, null, 0.0, ChatKnowledgeReaders.CHUNK_SIZE, ChatKnowledgeReaders.OVERLAP);
    }

    /**
     * @param closer 可关的 store（pg）；内存库可为 null
     * @param embeddingStore 用于 delete / 类型判断；应与 knowledge 内 store 同一对象
     */
    SimpleKnowledgeRetrievePort(
            SimpleKnowledge knowledge,
            AutoCloseable closer,
            VDBStoreBase embeddingStore,
            double scoreThreshold,
            int chunkSize,
            int chunkOverlap) {
        this.knowledge = Objects.requireNonNull(knowledge, "knowledge");
        this.store = closer;
        this.embeddingStore = embeddingStore;
        this.scoreThreshold = Math.max(0.0, Math.min(1.0, scoreThreshold));
        this.chunkSize = chunkSize > 0 ? chunkSize : ChatKnowledgeReaders.CHUNK_SIZE;
        this.chunkOverlap = Math.max(0, chunkOverlap);
    }

    /** 最近一次从 pg 导出的块。内存库或尚未重建时为空。 */
    public List<IngestedChunk> storedChunks() {
        return exported;
    }

    @Override
    public void setIngestProgressListener(IngestProgressListener listener) {
        if (listener == null) {
            PROGRESS.remove();
        } else {
            PROGRESS.set(listener);
        }
    }

    /**
     * 用与入库相同的 Reader 抽出纯文本块。embedding 失败后的关键词降级（含 PDF）走这里。
     */
    public static List<String> readPlainTexts(String filename, byte[] content, int chunkSize, int chunkOverlap) {
        List<Document> docs = ChatKnowledgeReaders.read(filename, content, chunkSize, chunkOverlap);
        if (docs == null || docs.isEmpty()) {
            return List.of();
        }
        List<String> texts = new ArrayList<>();
        for (Document doc : docs) {
            String text = textOf(doc);
            if (!text.isBlank()) {
                texts.add(text);
            }
        }
        return List.copyOf(texts);
    }

    /** 默认：内存向量库 + 阈值 0 + 默认切块。适合单测与无 PG 的本地演示。 */
    public static SimpleKnowledgeRetrievePort create(EmbeddingModel embedding) {
        return create(embedding, null, 0.0, ChatKnowledgeReaders.CHUNK_SIZE, ChatKnowledgeReaders.OVERLAP);
    }

    /**
     * 组装 {@link SimpleKnowledge}。{@code store == null} 时按 embedding 维度建 {@link InMemoryStore}。
     *
     * @param scoreThreshold 低于此分的命中不返回
     * @param chunkSize / chunkOverlap 仅影响后续 {@link #addFile}
     */
    public static SimpleKnowledgeRetrievePort create(
            EmbeddingModel embedding, VDBStoreBase store, double scoreThreshold, int chunkSize, int chunkOverlap) {
        Objects.requireNonNull(embedding, "embedding");
        int dims = embedding.getDimensions();
        if (dims <= 0) {
            throw new IllegalArgumentException("embedding dimensions must be > 0");
        }
        VDBStoreBase embeddingStore = store == null ? InMemoryStore.builder().dimensions(dims).build() : store;
        SimpleKnowledge knowledge = SimpleKnowledge.builder()
                .embeddingModel(embedding)
                .embeddingStore(embeddingStore)
                .build();
        AutoCloseable closer = embeddingStore instanceof AutoCloseable closeable ? closeable : null;
        return new SimpleKnowledgeRetrievePort(
                knowledge, closer, embeddingStore, scoreThreshold, chunkSize, chunkOverlap);
    }

    /** 兼容旧调用：默认阈值与切块。 */
    public static SimpleKnowledgeRetrievePort create(EmbeddingModel embedding, VDBStoreBase store) {
        return create(embedding, store, 0.0, ChatKnowledgeReaders.CHUNK_SIZE, ChatKnowledgeReaders.OVERLAP);
    }

    /** DashScope embedding + 内存向量库。 */
    public static SimpleKnowledgeRetrievePort dashScope(String apiKey) {
        return dashScope(apiKey, ChatEmbeddingSettings.defaults());
    }

    public static SimpleKnowledgeRetrievePort dashScope(String apiKey, ChatEmbeddingSettings embedding) {
        return dashScope(apiKey, embedding, 0.0, ChatKnowledgeReaders.CHUNK_SIZE, ChatKnowledgeReaders.OVERLAP);
    }

    public static SimpleKnowledgeRetrievePort dashScope(
            String apiKey,
            ChatEmbeddingSettings embedding,
            double scoreThreshold,
            int chunkSize,
            int chunkOverlap) {
        return create(dashScopeEmbedding(apiKey, embedding), null, scoreThreshold, chunkSize, chunkOverlap);
    }

    /**
     * DashScope embedding + 官方 {@link PgVectorStore}。
     *
     * <p>{@code build()} 会 {@code CREATE EXTENSION vector} 并建表；写入按 docId upsert。库不存在时先
     * {@link ChatPgVectorSettings#ensureDatabase()}。
     */
    public static SimpleKnowledgeRetrievePort dashScopePg(String apiKey, ChatPgVectorSettings pg) {
        return dashScopePg(apiKey, pg, ChatEmbeddingSettings.defaults());
    }

    public static SimpleKnowledgeRetrievePort dashScopePg(
            String apiKey, ChatPgVectorSettings pg, ChatEmbeddingSettings embedding) {
        return dashScopePg(apiKey, pg, embedding, 0.0, ChatKnowledgeReaders.CHUNK_SIZE, ChatKnowledgeReaders.OVERLAP);
    }

    public static SimpleKnowledgeRetrievePort dashScopePg(
            String apiKey,
            ChatPgVectorSettings pg,
            ChatEmbeddingSettings embedding,
            double scoreThreshold,
            int chunkSize,
            int chunkOverlap) {
        Objects.requireNonNull(pg, "pg");
        if (!pg.configured()) {
            throw new IllegalArgumentException("dream-scope.rag.pg.jdbc-url required");
        }
        EmbeddingModel model = dashScopeEmbedding(apiKey, embedding);
        PgVectorStore store;
        try {
            pg.ensureDatabase();
            store = PgVectorStore.builder()
                    .jdbcUrl(pg.jdbcUrl())
                    .username(pg.username())
                    .password(pg.password())
                    .schema(pg.schema())
                    .tableName(pg.tableName())
                    .dimensions(model.getDimensions())
                    .build();
        } catch (Exception ex) {
            Throwable root = rootCause(ex);
            throw new IllegalStateException(
                    "pgvector store failed: " + pg.jdbcUrl() + " : " + root.getMessage(), ex);
        }
        log.info(
                "pgvector store jdbc={} schema={} table={} embedding={} dims={}",
                pg.jdbcUrl(),
                pg.schema(),
                pg.tableName(),
                modelName(embedding),
                model.getDimensions());
        return create(model, store, scoreThreshold, chunkSize, chunkOverlap);
    }

    /** 构造百炼 {@link DashScopeTextEmbedding}；模型名与维度来自 {@link ChatEmbeddingSettings}。 */
    static EmbeddingModel dashScopeEmbedding(String apiKey, ChatEmbeddingSettings embedding) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("dream-scope.model.api-key required");
        }
        ChatEmbeddingSettings settings = embedding == null ? ChatEmbeddingSettings.defaults() : embedding;
        return DashScopeTextEmbedding.builder()
                .apiKey(apiKey.trim())
                .modelName(settings.modelName())
                .dimensions(settings.dimensions())
                .build();
    }

    private static String modelName(ChatEmbeddingSettings embedding) {
        return embedding == null ? ChatEmbeddingSettings.DEFAULT_MODEL : embedding.modelName();
    }

    /** 便捷入口：不指定块 id。 */
    public String addText(String text, String source, String docType) {
        return addText(null, text, source, docType);
    }

    /**
     * 入库一段文本（整段一块，不再切分）。
     *
     * <p>payload 写入 {@code source} / {@code docType}，检索命中与按源删除都依赖它们。失败抛
     * {@link IllegalStateException}（额度不足等由上层 Hybrid / RagPortsConfig 识别）。
     */
    @Override
    public String addText(String id, String text, String source, String docType) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String docId = id == null || id.isBlank() ? "sk-" + UUID.randomUUID().toString().replace("-", "") : id.trim();
        DocumentMetadata meta = DocumentMetadata.builder()
                .content(TextBlock.builder().text(text.trim()).build())
                .docId(docId)
                .chunkId(docId)
                .addPayload("source", source == null ? "" : source)
                .addPayload("docType", docType == null ? "" : docType.trim())
                .build();
        try {
            knowledge.addDocuments(List.of(new Document(meta))).block(TIMEOUT);
            sourceIds.compute(source == null ? "" : source, (k, old) -> {
                List<String> next = old == null ? new ArrayList<>() : new ArrayList<>(old);
                next.add(docId);
                return List.copyOf(next);
            });
            log.info("simple rag ingest id={} source={} chars={}", docId, source, text.trim().length());
            return docId;
        } catch (RuntimeException ex) {
            throw new IllegalStateException("simple knowledge ingest failed", ex);
        }
    }

    /**
     * 文件入库：官方 Reader 切块 → 打稳定 id / source → 分批 embed 写入。
     *
     * <ol>
     *   <li>先 {@link ChatKnowledgeReaders#read}；非法类型抛 {@link IllegalArgumentException}。
     *   <li>同 {@code source} 先 {@link #deleteBySource}，避免重传叠两套向量。
     *   <li>块 id 形如 {@code base}、{@code base-2}…（见 {@link KnowledgeTextFile#chunkIds}）。
     *   <li>每批 8 块写入，降低大 PDF 一次请求超时概率；进度回调 {@code reading} / {@code embedding}。
     *   <li>embed 失败抛 {@link EmbeddingIngestException}（已带抽出的正文），Hybrid 可回退关键词。
     * </ol>
     */
    @Override
    public List<String> addFile(String id, String filename, byte[] content, String source, String docType) {
        List<IngestedChunk> written = addFileChunks(id, filename, content, source, docType);
        List<String> ids = new ArrayList<>(written.size());
        for (IngestedChunk chunk : written) {
            ids.add(chunk.id());
        }
        return List.copyOf(ids);
    }

    @Override
    public List<IngestedChunk> addFileChunks(String id, String filename, byte[] content, String source, String docType) {
        List<Document> raw;
        try {
            raw = ChatKnowledgeReaders.read(filename, content, chunkSize, chunkOverlap);
        } catch (RuntimeException ex) {
            if (ex instanceof IllegalArgumentException) {
                throw ex;
            }
            throw new IllegalStateException("simple knowledge read failed", ex);
        }
        if (raw == null || raw.isEmpty()) {
            throw new IllegalArgumentException("reader produced no documents");
        }
        String src = source == null ? "" : source;
        String type = docType == null || docType.isBlank() ? "file" : docType.trim();
        String base = id == null || id.isBlank() ? KnowledgeTextFile.stemId(filename) : id.trim();
        deleteBySource(src);
        notifyProgress("reading", 0, raw.size());
        List<String> ids = KnowledgeTextFile.chunkIds(base, raw.size());
        List<Document> stamped = new ArrayList<>(raw.size());
        List<String> texts = new ArrayList<>(raw.size());
        for (int i = 0; i < raw.size(); i++) {
            stamped.add(stamp(raw.get(i), ids.get(i), src, type));
            texts.add(textOf(raw.get(i)));
        }
        notifyProgress("reading", raw.size(), raw.size());
        log.info(
                "simple rag file read name={} reader={} chunks={}",
                filename,
                ChatKnowledgeReaders.readerName(filename),
                stamped.size());
        // 大文件分批：整本一次 block 容易卡死 HTTP/Apifox，也更容易撞 embedding 超时。
        int batchSize = 8;
        try {
            for (int from = 0; from < stamped.size(); from += batchSize) {
                int to = Math.min(stamped.size(), from + batchSize);
                knowledge.addDocuments(stamped.subList(from, to)).block(Duration.ofMinutes(2));
                notifyProgress("embedding", to, stamped.size());
                log.info("simple rag ingest file progress name={} {}/{}", filename, to, stamped.size());
            }
            sourceIds.put(src, List.copyOf(ids));
            List<IngestedChunk> written = new ArrayList<>(ids.size());
            for (int i = 0; i < ids.size(); i++) {
                written.add(new IngestedChunk(ids.get(i), texts.get(i), src, type));
            }
            log.info(
                    "simple rag ingest file done reader={} name={} chunks={} source={}",
                    ChatKnowledgeReaders.readerName(filename),
                    filename,
                    stamped.size(),
                    src);
            return List.copyOf(written);
        } catch (RuntimeException ex) {
            // 正文已抽出：把 texts 带给上层，避免额度不足时整份 PDF 白读。
            throw new EmbeddingIngestException(
                    "simple knowledge ingest failed", base, src, type, texts, ex);
        }
    }

    /**
     * 当前进程见过的来源及块数。只反映 {@link #sourceIds}；空 source 不列出。
     */
    @Override
    public List<KnowledgeSource> sources() {
        List<KnowledgeSource> out = new ArrayList<>();
        sourceIds.forEach((source, ids) -> {
            if (source != null && !source.isBlank() && ids != null && !ids.isEmpty()) {
                out.add(new KnowledgeSource(source, ids.size()));
            }
        });
        out.sort(java.util.Comparator.comparing(KnowledgeSource::source));
        return List.copyOf(out);
    }

    /**
     * 按 source 删除向量块。返回成功从向量库删掉的条数。关键词由 Hybrid 自己删。
     *
     * <p>若重启后未 {@link #rebuildSourceIndexFromStore()}，{@code sourceIds} 为空则删不到旧向量。
     */
    @Override
    public int deleteBySource(String source) {
        String src = source == null ? "" : source;
        List<String> ids = sourceIds.remove(src);
        if (ids == null || ids.isEmpty() || embeddingStore == null) {
            return 0;
        }
        int deleted = 0;
        for (String docId : ids) {
            try {
                Boolean ok = embeddingStore.delete(docId).block(TIMEOUT);
                if (Boolean.TRUE.equals(ok)) {
                    deleted++;
                }
            } catch (RuntimeException ex) {
                log.warn("rag delete id={} failed: {}", docId, ex.getMessage());
            }
        }
        log.info("simple rag deleteBySource source={} deleted={}", src, deleted);
        return deleted;
    }

    /**
     * 从 pgvector 表重建 {@code source → docIds}，正文留在 {@link #storedChunks()} 供 Hybrid 回填。
     *
     * <p>仅 pg 有效；内存库返回 0。组合根在「表非空、跳过 seed」时调用，保证重启后列表/删除/混合检索仍可用。
     *
     * @return 恢复的文档块行数；查询失败返回 0（不抛）
     */
    public int rebuildSourceIndexFromStore() {
        if (!(store instanceof PgVectorStore pg)) {
            return 0;
        }
        String sql = "SELECT doc_id, content, COALESCE(payload->>'source',''), COALESCE(payload->>'docType','') FROM "
                + quoteIdent(pg.getSchema())
                + "."
                + quoteIdent(pg.getTableName());
        Map<String, List<String>> rebuilt = new ConcurrentHashMap<>();
        List<IngestedChunk> chunks = new ArrayList<>();
        int rows = 0;
        try (Statement statement = pg.getConnection().createStatement();
                ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                String docId = rs.getString(1);
                String content = rs.getString(2);
                String source = rs.getString(3);
                String docType = rs.getString(4);
                if (docId == null || docId.isBlank()) {
                    continue;
                }
                String src = source == null ? "" : source;
                rebuilt.compute(src, (k, old) -> {
                    List<String> next = old == null ? new ArrayList<>() : new ArrayList<>(old);
                    next.add(docId);
                    return next;
                });
                chunks.add(new IngestedChunk(docId, content == null ? "" : content, src, docType == null ? "" : docType));
                rows++;
            }
        } catch (Exception ex) {
            log.warn("pgvector rebuild source index failed: {}", ex.getMessage());
            return 0;
        }
        sourceIds.clear();
        rebuilt.forEach((source, ids) -> sourceIds.put(source, List.copyOf(ids)));
        exported = List.copyOf(chunks);
        log.info("simple rag rebuilt source index rows={} sources={}", rows, sourceIds.size());
        return rows;
    }

    private void notifyProgress(String stage, int done, int total) {
        IngestProgressListener listener = PROGRESS.get();
        if (listener == null) {
            return;
        }
        try {
            listener.onProgress(stage, done, total);
        } catch (RuntimeException ex) {
            log.warn("ingest progress listener failed: {}", ex.getMessage());
        }
    }

    private static String textOf(Document doc) {
        DocumentMetadata meta = doc == null ? null : doc.getMetadata();
        String text = meta == null ? "" : meta.getContentText();
        return text == null ? "" : text;
    }

    /**
     * 给 Reader 产出的块盖上稳定 id 与 payload。AgentScope Reader 自带的 id 不可靠，覆盖写入依赖我们自己的
     * {@code docId}/{@code source}。
     */
    private static Document stamp(Document doc, String docId, String source, String docType) {
        DocumentMetadata old = doc == null ? null : doc.getMetadata();
        ContentBlock content = old == null || old.getContent() == null
                ? TextBlock.builder().text("").build()
                : old.getContent();
        DocumentMetadata meta = DocumentMetadata.builder()
                .content(content)
                .docId(docId)
                .chunkId(docId)
                .addPayload("source", source)
                .addPayload("docType", docType)
                .build();
        return new Document(meta);
    }

    /**
     * pg 表是否已有数据。内存库恒为 false，以便每次启动灌演示语料；pg 有行则跳过 seed，避免重复 upsert。
     */
    public boolean hasStoredDocuments() {
        if (!(store instanceof PgVectorStore pg)) {
            return false;
        }
        String sql = "SELECT 1 FROM " + quoteIdent(pg.getSchema()) + "." + quoteIdent(pg.getTableName()) + " LIMIT 1";
        try (Statement statement = pg.getConnection().createStatement();
                ResultSet rs = statement.executeQuery(sql)) {
            return rs.next();
        } catch (Exception ex) {
            log.warn("pgvector empty-check failed: {}", ex.getMessage());
            return false;
        }
    }

    /** SQL 标识符双引号转义，避免 schema/table 名注入。 */
    private static String quoteIdent(String ident) {
        String raw = ident == null || ident.isBlank() ? "public" : ident;
        return "\"" + raw.replace("\"", "\"\"") + "\"";
    }

    /**
     * 向量相似度检索。只走本库，不做关键词融合；融合在外层 Hybrid。
     *
     * @param topK 上限条数，同时写入 {@link RetrieveConfig#limit}
     */
    @Override
    public List<RetrieveHit> retrieve(String query, int topK) {
        if (query == null || query.isBlank() || topK <= 0) {
            return List.of();
        }
        RetrieveConfig config =
                RetrieveConfig.builder().limit(topK).scoreThreshold(scoreThreshold).build();
        List<Document> docs;
        try {
            docs = knowledge.retrieve(query, config).block(TIMEOUT);
        } catch (RuntimeException ex) {
            log.warn("simple rag retrieve failed queryChars={}", query.length(), ex);
            throw new IllegalStateException("simple knowledge retrieve failed", ex);
        }
        if (docs == null || docs.isEmpty()) {
            log.info("simple rag retrieve miss queryChars={} preview={}", query.length(), LogText.preview(query, 80));
            return List.of();
        }
        List<RetrieveHit> hits = new ArrayList<>(docs.size());
        for (Document doc : docs) {
            hits.add(toHit(doc));
        }
        log.info(
                "simple rag retrieve hits={} queryChars={} preview={}",
                hits.size(),
                query.length(),
                LogText.preview(query, 80));
        return List.copyOf(hits);
    }

    /** 关掉 pg 连接等；Spring {@code destroyMethod=close} 会调到这里。 */
    @Override
    public void close() {
        if (store == null) {
            return;
        }
        try {
            store.close();
        } catch (Exception ex) {
            log.warn("rag store close failed: {}", ex.getMessage());
        }
    }

    /** AgentScope Document → domain {@link RetrieveHit}；source/docType 优先读 payload。 */
    private static RetrieveHit toHit(Document doc) {
        DocumentMetadata meta = doc.getMetadata();
        String text = meta == null ? "" : meta.getContentText();
        String id = meta == null ? "" : meta.getDocId();
        if (id == null || id.isBlank()) {
            id = doc.getId();
        }
        double score = doc.getScore() == null ? 0.0 : doc.getScore();
        return new RetrieveHit(id, text, score, payloadString(doc, meta, "source"), payloadString(doc, meta, "docType"));
    }

    private static String payloadString(Document doc, DocumentMetadata meta, String key) {
        Object value = doc.getPayloadValue(key);
        if (value == null && meta != null) {
            value = meta.getPayloadValue(key);
        }
        return value == null ? "" : String.valueOf(value);
    }

    private static Throwable rootCause(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }
}

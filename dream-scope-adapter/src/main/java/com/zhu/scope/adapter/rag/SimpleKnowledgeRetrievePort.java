package com.zhu.scope.adapter.rag;

import com.zhu.scope.adapter.LogText;
import com.zhu.scope.knowledge.EmbeddingIngestException;
import com.zhu.scope.knowledge.IngestProgressListener;
import com.zhu.scope.knowledge.KnowledgeSource;
import com.zhu.scope.knowledge.RetrieveHit;
import com.zhu.scope.knowledge.RetrievePort;
import com.zhu.scope.rag.InMemoryKeywordIndex;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 官方 {@code agentscope-extensions-rag-simple}：{@link SimpleKnowledge} + {@link InMemoryStore}
 * 或 {@link PgVectorStore} 实现 {@link RetrievePort}。domain / knowledge 不出现 {@code io.agentscope}。
 *
 * <p>不接 {@code ReActAgent.knowledge()} / {@code RAGMode.AGENTIC}，避免与 {@code ChatTools.retrieve} 双通道。
 *
 * @see <a href="https://java.agentscope.io/v2/en/integration/rag/simple.html">Simple Knowledge</a>
 */
@SuppressWarnings({"deprecation", "removal"})
public final class SimpleKnowledgeRetrievePort implements RetrievePort {

    private static final Logger log = LoggerFactory.getLogger(SimpleKnowledgeRetrievePort.class);

    public static final String DEFAULT_EMBEDDING_MODEL = ChatEmbeddingSettings.DEFAULT_MODEL;

    public static final int DEFAULT_DIMENSIONS = ChatEmbeddingSettings.DEFAULT_DIMENSIONS;

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final SimpleKnowledge knowledge;

    private final AutoCloseable store;

    private final VDBStoreBase embeddingStore;

    private final AtomicInteger seq = new AtomicInteger();

    private final double scoreThreshold;

    private final int chunkSize;

    private final int chunkOverlap;

    private final Map<String, List<String>> sourceIds = new ConcurrentHashMap<>();

    private InMemoryKeywordIndex keywordMirror;

    private IngestProgressListener progressListener;

    public SimpleKnowledgeRetrievePort(SimpleKnowledge knowledge) {
        this(knowledge, null, null, 0.0, ChatKnowledgeReaders.CHUNK_SIZE, ChatKnowledgeReaders.OVERLAP);
    }

    SimpleKnowledgeRetrievePort(SimpleKnowledge knowledge, AutoCloseable store) {
        this(knowledge, store, null, 0.0, ChatKnowledgeReaders.CHUNK_SIZE, ChatKnowledgeReaders.OVERLAP);
    }

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

    /** 向量写入成功后同步一份到关键词，供 Hybrid RRF。 */
    public void mirrorKeyword(InMemoryKeywordIndex index) {
        this.keywordMirror = index;
    }

    @Override
    public void setIngestProgressListener(IngestProgressListener listener) {
        this.progressListener = listener;
    }

    public static SimpleKnowledgeRetrievePort create(EmbeddingModel embedding) {
        return create(embedding, null, 0.0, ChatKnowledgeReaders.CHUNK_SIZE, ChatKnowledgeReaders.OVERLAP);
    }

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
     * DashScope embedding + 官方 {@link PgVectorStore}。{@code build()} 会 {@code CREATE EXTENSION vector} 并建表，
     * 写入是主键 upsert。
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

    public String addText(String text, String source, String docType) {
        return addText(null, text, source, docType);
    }

    @Override
    public String addText(String id, String text, String source, String docType) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String docId = id == null || id.isBlank() ? "sk-" + seq.incrementAndGet() : id.trim();
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
            mirrorKeywordChunk(docId, text.trim(), source == null ? "" : source, docType == null ? "" : docType.trim());
            log.info("simple rag ingest id={} source={} chars={}", docId, source, text.trim().length());
            return docId;
        } catch (RuntimeException ex) {
            throw new IllegalStateException("simple knowledge ingest failed", ex);
        }
    }

    @Override
    public List<String> addFile(String id, String filename, byte[] content, String source, String docType) {
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
        int batchSize = 8;
        try {
            for (int from = 0; from < stamped.size(); from += batchSize) {
                int to = Math.min(stamped.size(), from + batchSize);
                knowledge.addDocuments(stamped.subList(from, to)).block(Duration.ofMinutes(2));
                notifyProgress("embedding", to, stamped.size());
                log.info("simple rag ingest file progress name={} {}/{}", filename, to, stamped.size());
            }
            sourceIds.put(src, List.copyOf(ids));
            mirrorKeywordChunks(ids, texts, src, type);
            log.info(
                    "simple rag ingest file done reader={} name={} chunks={} source={}",
                    ChatKnowledgeReaders.readerName(filename),
                    filename,
                    stamped.size(),
                    src);
            return List.copyOf(ids);
        } catch (RuntimeException ex) {
            throw new EmbeddingIngestException(
                    "simple knowledge ingest failed", base, src, type, texts, ex);
        }
    }

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

    @Override
    public int deleteBySource(String source) {
        String src = source == null ? "" : source;
        List<String> ids = sourceIds.remove(src);
        if (keywordMirror != null) {
            keywordMirror.deleteBySource(src);
        }
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
     * 从 pgvector 表重建 {@code source → docIds}。进程重启后列表/按源删除依赖此方法。
     *
     * @return 恢复的文档块数
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
        List<Object[]> keywordRows = new ArrayList<>();
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
                keywordRows.add(new Object[] {docId, content == null ? "" : content, src, docType == null ? "" : docType});
                rows++;
            }
        } catch (Exception ex) {
            log.warn("pgvector rebuild source index failed: {}", ex.getMessage());
            return 0;
        }
        sourceIds.clear();
        rebuilt.forEach((source, ids) -> sourceIds.put(source, List.copyOf(ids)));
        if (keywordMirror != null) {
            for (String source : rebuilt.keySet()) {
                keywordMirror.deleteBySource(source);
            }
            for (Object[] row : keywordRows) {
                String text = (String) row[1];
                if (text.isBlank()) {
                    continue;
                }
                keywordMirror.addText((String) row[0], text, (String) row[2], (String) row[3]);
            }
        }
        log.info("simple rag rebuilt source index rows={} sources={}", rows, sourceIds.size());
        return rows;
    }

    private void mirrorKeywordChunk(String id, String text, String source, String docType) {
        if (keywordMirror == null || text == null || text.isBlank()) {
            return;
        }
        try {
            keywordMirror.addText(id, text, source, docType);
        } catch (RuntimeException ex) {
            log.warn("keyword mirror addText failed id={}: {}", id, ex.getMessage());
        }
    }

    private void mirrorKeywordChunks(List<String> ids, List<String> texts, String source, String docType) {
        if (keywordMirror == null || texts == null || texts.isEmpty()) {
            return;
        }
        try {
            keywordMirror.deleteBySource(source);
            for (int i = 0; i < texts.size(); i++) {
                keywordMirror.addText(ids.get(i), texts.get(i), source, docType);
            }
        } catch (RuntimeException ex) {
            log.warn("keyword mirror addFile failed source={}: {}", source, ex.getMessage());
        }
    }

    private void notifyProgress(String stage, int done, int total) {
        IngestProgressListener listener = progressListener;
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

    /** pgvector 表里已有行则 true；内存库总是 false（每次启动再灌演示语料）。 */
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

    private static String quoteIdent(String ident) {
        String raw = ident == null || ident.isBlank() ? "public" : ident;
        return "\"" + raw.replace("\"", "\"\"") + "\"";
    }

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

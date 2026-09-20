package com.zhu.scope.adapter.rag;

import com.zhu.scope.knowledge.RetrieveHit;
import com.zhu.scope.knowledge.RetrievePort;
import io.agentscope.core.embedding.EmbeddingModel;
import io.agentscope.core.embedding.dashscope.DashScopeTextEmbedding;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.rag.knowledge.SimpleKnowledge;
import io.agentscope.core.rag.model.Document;
import io.agentscope.core.rag.model.DocumentMetadata;
import io.agentscope.core.rag.model.RetrieveConfig;
import io.agentscope.core.rag.store.InMemoryStore;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 官方 {@code agentscope-extensions-rag-simple}：{@link SimpleKnowledge} + {@link InMemoryStore} 实现
 * {@link RetrievePort}。domain / knowledge 模块不出现 {@code io.agentscope}。
 *
 * <p>不接 {@code ReActAgent.knowledge()} / {@code RAGMode.AGENTIC}，避免与 {@code ChatTools.retrieve} 双通道。
 *
 * <p>{@code Document} / {@code DocumentMetadata} / {@code RetrieveConfig} 在 core 自 2.0.0 {@code forRemoval}，
 * 但 2.0.3 扩展 {@link SimpleKnowledge#addDocuments} / {@link SimpleKnowledge#retrieve} 的参数仍是它们
 *（官方 quickstart 同样直接用）。没有非弃用替代类型，故对本类压制告警。
 *
 * @see <a href="https://java.agentscope.io/v2/en/integration/rag/simple.html">Simple Knowledge</a>
 */
@SuppressWarnings({"deprecation", "removal"})
public final class SimpleKnowledgeRetrievePort implements RetrievePort {

    public static final String DEFAULT_EMBEDDING_MODEL = "text-embedding-v3";

    public static final int DEFAULT_DIMENSIONS = 1024;

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final SimpleKnowledge knowledge;

    private final AtomicInteger seq = new AtomicInteger();

    public SimpleKnowledgeRetrievePort(SimpleKnowledge knowledge) {
        this.knowledge = Objects.requireNonNull(knowledge, "knowledge");
    }

    public static SimpleKnowledgeRetrievePort create(EmbeddingModel embedding) {
        Objects.requireNonNull(embedding, "embedding");
        int dims = embedding.getDimensions();
        if (dims <= 0) {
            throw new IllegalArgumentException("embedding dimensions must be > 0");
        }
        SimpleKnowledge knowledge = SimpleKnowledge.builder()
                .embeddingModel(embedding)
                .embeddingStore(InMemoryStore.builder().dimensions(dims).build())
                .build();
        return new SimpleKnowledgeRetrievePort(knowledge);
    }

    public static SimpleKnowledgeRetrievePort dashScope(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("DASHSCOPE_API_KEY required");
        }
        EmbeddingModel embedding = DashScopeTextEmbedding.builder()
                .apiKey(apiKey.trim())
                .modelName(DEFAULT_EMBEDDING_MODEL)
                .dimensions(DEFAULT_DIMENSIONS)
                .build();
        return create(embedding);
    }

    public void addText(String text, String source, String docType) {
        if (text == null || text.isBlank()) {
            return;
        }
        String id = "sk-" + seq.incrementAndGet();
        DocumentMetadata meta = DocumentMetadata.builder()
                .content(TextBlock.builder().text(text.trim()).build())
                .docId(id)
                .chunkId(id)
                .addPayload("source", source == null ? "" : source)
                .addPayload("docType", docType == null ? "" : docType.trim())
                .build();
        try {
            knowledge.addDocuments(List.of(new Document(meta))).block(TIMEOUT);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("simple knowledge ingest failed", ex);
        }
    }

    @Override
    public List<RetrieveHit> retrieve(String query, int topK) {
        if (query == null || query.isBlank() || topK <= 0) {
            return List.of();
        }
        RetrieveConfig config = RetrieveConfig.builder().limit(topK).scoreThreshold(0.0).build();
        List<Document> docs;
        try {
            docs = knowledge.retrieve(query, config).block(TIMEOUT);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("simple knowledge retrieve failed", ex);
        }
        if (docs == null || docs.isEmpty()) {
            return List.of();
        }
        List<RetrieveHit> hits = new ArrayList<>(docs.size());
        for (Document doc : docs) {
            hits.add(toHit(doc));
        }
        return List.copyOf(hits);
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
}

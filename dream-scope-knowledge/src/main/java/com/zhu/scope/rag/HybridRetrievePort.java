package com.zhu.scope.rag;

import com.zhu.scope.knowledge.EmbeddingIngestException;
import com.zhu.scope.knowledge.IngestProgressListener;
import com.zhu.scope.knowledge.KnowledgeSource;
import com.zhu.scope.knowledge.RetrieveHit;
import com.zhu.scope.knowledge.RetrievePort;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 真混合检索：向量与关键词双路召回，RRF 融合。embedding 失败时正文仍进关键词。
 *
 * <p>成功写入向量库后的关键词镜像由 {@code SimpleKnowledgeRetrievePort#mirrorKeyword} 负责，
 * 本类在失败路径写入关键词，检索时双路合并。
 */
public final class HybridRetrievePort implements RetrievePort {

    private static final Logger log = LoggerFactory.getLogger(HybridRetrievePort.class);

    static final int RRF_K = 60;

    private final RetrievePort primary;

    private final InMemoryKeywordIndex keyword;

    public HybridRetrievePort(RetrievePort primary, InMemoryKeywordIndex keyword) {
        this.primary = primary;
        this.keyword = keyword == null ? new InMemoryKeywordIndex() : keyword;
    }

    public HybridRetrievePort(RetrievePort primary) {
        this(primary, new InMemoryKeywordIndex());
    }

    /** 供组合根把同一份关键词索引交给向量实现做镜像。 */
    public InMemoryKeywordIndex keywordIndex() {
        return keyword;
    }

    @Override
    public List<RetrieveHit> retrieve(String query, int topK) {
        return retrieve(query, topK, null);
    }

    @Override
    public List<RetrieveHit> retrieve(String query, int topK, String source) {
        if (query == null || query.isBlank() || topK <= 0) {
            return List.of();
        }
        int fetch = Math.min(Math.max(topK * 3, topK), 32);
        List<RetrieveHit> vector = safeRetrieve(primary, query, fetch, source);
        List<RetrieveHit> kw = safeRetrieve(keyword, query, fetch, source);
        if (vector.isEmpty() && kw.isEmpty()) {
            log.info("hybrid retrieve miss topK={} source={} chars={}", topK, source == null ? "" : source, query.length());
            return List.of();
        }
        if (vector.isEmpty()) {
            List<RetrieveHit> out = trim(kw, topK);
            log.info("hybrid retrieve mode=keyword hits={} kw={} chars={}", out.size(), kw.size(), query.length());
            return out;
        }
        if (kw.isEmpty()) {
            List<RetrieveHit> out = trim(vector, topK);
            log.info("hybrid retrieve mode=vector hits={} vector={} chars={}", out.size(), vector.size(), query.length());
            return out;
        }
        List<RetrieveHit> fused = fuseRrf(vector, kw, topK);
        log.info(
                "hybrid retrieve mode=rrf hits={} vector={} keyword={} chars={}",
                fused.size(),
                vector.size(),
                kw.size(),
                query.length());
        return fused;
    }

    static List<RetrieveHit> fuseRrf(List<RetrieveHit> left, List<RetrieveHit> right, int topK) {
        Map<String, double[]> scores = new HashMap<>();
        Map<String, RetrieveHit> docs = new LinkedHashMap<>();
        addRanks(left, scores, docs);
        addRanks(right, scores, docs);
        List<Map.Entry<String, double[]>> ranked = new ArrayList<>(scores.entrySet());
        ranked.sort(Comparator.comparingDouble((Map.Entry<String, double[]> e) -> e.getValue()[0]).reversed());
        int limit = Math.min(topK, ranked.size());
        List<RetrieveHit> out = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            String id = ranked.get(i).getKey();
            RetrieveHit hit = docs.get(id);
            out.add(new RetrieveHit(hit.id(), hit.text(), ranked.get(i).getValue()[0], hit.source(), hit.docType()));
        }
        return List.copyOf(out);
    }

    private static void addRanks(
            List<RetrieveHit> hits, Map<String, double[]> scores, Map<String, RetrieveHit> docs) {
        for (int i = 0; i < hits.size(); i++) {
            RetrieveHit hit = hits.get(i);
            String id = hit.id() == null || hit.id().isBlank() ? "\0" + i + "\0" + hit.text() : hit.id();
            final int rank = i;
            scores.compute(id, (k, old) -> {
                double next = (old == null ? 0.0 : old[0]) + 1.0 / (RRF_K + rank + 1);
                return new double[] {next};
            });
            docs.putIfAbsent(id, hit);
        }
    }

    private static List<RetrieveHit> safeRetrieve(RetrievePort port, String query, int topK, String source) {
        try {
            List<RetrieveHit> hits = source == null || source.isBlank()
                    ? port.retrieve(query, topK)
                    : port.retrieve(query, topK, source);
            return hits == null ? List.of() : hits;
        } catch (RuntimeException ex) {
            log.warn("hybrid branch retrieve failed: {}", ex.getMessage());
            return List.of();
        }
    }

    private static List<RetrieveHit> trim(List<RetrieveHit> hits, int topK) {
        if (hits.size() <= topK) {
            return hits;
        }
        return List.copyOf(hits.subList(0, topK));
    }

    @Override
    public List<KnowledgeSource> sources() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        mergeSources(counts, primary.sources());
        mergeSources(counts, keyword.sources());
        List<KnowledgeSource> out = new ArrayList<>(counts.size());
        counts.forEach((source, chunks) -> out.add(new KnowledgeSource(source, chunks)));
        return List.copyOf(out);
    }

    private static void mergeSources(Map<String, Integer> counts, List<KnowledgeSource> sources) {
        if (sources == null) {
            return;
        }
        for (KnowledgeSource source : sources) {
            if (source == null || source.source().isBlank()) {
                continue;
            }
            counts.merge(source.source(), source.chunks(), Math::max);
        }
    }

    @Override
    public String addText(String id, String text, String source, String docType) {
        try {
            return primary.addText(id, text, source, docType);
        } catch (RuntimeException ex) {
            if (!isEmbeddingFailure(ex)) {
                throw ex;
            }
            log.warn("hybrid addText embedding failed, keyword fallback: {}", ex.getMessage());
            return keyword.addText(id, text, source, docType);
        }
    }

    @Override
    public List<String> addFile(String id, String filename, byte[] content, String source, String docType) {
        try {
            return primary.addFile(id, filename, content, source, docType);
        } catch (EmbeddingIngestException ex) {
            log.warn(
                    "hybrid addFile embedding failed, keyword fallback name={} chunks={}: {}",
                    filename,
                    ex.texts().size(),
                    ex.getMessage());
            return addKeywordChunks(ex.baseId(), ex.texts(), ex.source(), ex.docType());
        } catch (RuntimeException ex) {
            if (!isEmbeddingFailure(ex)) {
                throw ex;
            }
            log.warn("hybrid addFile embedding failed, keyword fallback name={}: {}", filename, ex.getMessage());
            String name = KnowledgeTextFile.basename(filename);
            if (!KnowledgeTextFile.supported(name)) {
                throw ex;
            }
            String src = source == null || source.isBlank() ? name : source.trim();
            String type = docType == null || docType.isBlank() ? "file" : docType.trim();
            String base = id == null || id.isBlank() ? KnowledgeTextFile.stemId(name) : id.trim();
            return keyword.addFile(base, name, content, src, type);
        }
    }

    @Override
    public int deleteBySource(String source) {
        return primary.deleteBySource(source) + keyword.deleteBySource(source);
    }

    @Override
    public void setIngestProgressListener(IngestProgressListener listener) {
        primary.setIngestProgressListener(listener);
    }

    @Override
    public void close() {
        primary.close();
    }

    List<String> addKeywordChunks(String baseId, List<String> texts, String source, String docType) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        String src = source == null ? "" : source;
        String type = docType == null || docType.isBlank() ? "file" : docType.trim();
        keyword.deleteBySource(src);
        List<String> ids = KnowledgeTextFile.chunkIds(baseId, texts.size());
        for (int i = 0; i < texts.size(); i++) {
            keyword.addText(ids.get(i), texts.get(i), src, type);
        }
        return ids;
    }

    static boolean isEmbeddingFailure(Throwable error) {
        if (error instanceof EmbeddingIngestException) {
            return true;
        }
        Throwable current = error;
        while (current != null) {
            String msg = current.getMessage() == null ? "" : current.getMessage();
            String type = current.getClass().getName();
            if (msg.contains("Free quota")
                    || msg.contains("FreeTierOnly")
                    || msg.contains("AllocationQuota")
                    || msg.contains("simple knowledge ingest failed")
                    || type.contains("EmbeddingException")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}

package com.zhu.scope.rag;

import com.zhu.scope.knowledge.RetrieveHit;
import com.zhu.scope.knowledge.RetrievePort;
import com.zhu.scope.knowledge.KnowledgeSource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 进程内关键词检索。无向量库；入库后按词项重叠打分。
 */
public final class InMemoryKeywordIndex implements RetrievePort {

    private final CopyOnWriteArrayList<Chunk> chunks = new CopyOnWriteArrayList<>();

    private final double minScore;

    private final int chunkChars;

    private final int chunkOverlap;

    public InMemoryKeywordIndex() {
        this(0.0);
    }

    public InMemoryKeywordIndex(double minScore) {
        this(minScore, KnowledgeTextFile.CHUNK_CHARS, KnowledgeTextFile.CHUNK_OVERLAP);
    }

    public InMemoryKeywordIndex(double minScore, int chunkChars, int chunkOverlap) {
        if (minScore < 0.0 || minScore > 1.0) {
            throw new IllegalArgumentException("minScore must be in [0, 1]");
        }
        this.minScore = minScore;
        this.chunkChars = chunkChars > 0 ? chunkChars : KnowledgeTextFile.CHUNK_CHARS;
        this.chunkOverlap = Math.max(0, chunkOverlap);
    }

    public void ingest(String text, String source, String docType) {
        addText(null, text, source, docType);
    }

    @Override
    public String addText(String id, String text, String source, String docType) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String docId = id == null || id.isBlank() ? "kw-" + UUID.randomUUID().toString().replace("-", "") : id.trim();
        chunks.add(new Chunk(
                docId,
                text.trim(),
                source == null ? "" : source,
                docType == null ? "" : docType.trim()));
        return docId;
    }

    @Override
    public List<String> addFile(String id, String filename, byte[] content, String source, String docType) {
        String name = KnowledgeTextFile.basename(filename);
        if (!KnowledgeTextFile.supported(name)) {
            throw new IllegalArgumentException("keyword index only supports text files");
        }
        String text = KnowledgeTextFile.decodeUtf8(content);
        List<String> parts = KnowledgeTextFile.chunks(text, chunkChars, chunkOverlap);
        if (parts.isEmpty()) {
            throw new IllegalArgumentException("file text required");
        }
        String src = source == null || source.isBlank() ? name : source.trim();
        String type = docType == null || docType.isBlank() ? "file" : docType.trim();
        String base = id == null || id.isBlank() ? KnowledgeTextFile.stemId(name) : id.trim();
        deleteBySource(src);
        List<String> ids = KnowledgeTextFile.chunkIds(base, parts.size());
        for (int i = 0; i < parts.size(); i++) {
            addText(ids.get(i), parts.get(i), src, type);
        }
        return ids;
    }

    public void clear() {
        chunks.clear();
    }

    @Override
    public int deleteBySource(String source) {
        String src = source == null ? "" : source;
        List<Chunk> kept = new ArrayList<>();
        int removed = 0;
        for (Chunk chunk : chunks) {
            if (src.equals(chunk.source())) {
                removed++;
            } else {
                kept.add(chunk);
            }
        }
        if (removed == 0) {
            return 0;
        }
        chunks.clear();
        chunks.addAll(kept);
        return removed;
    }

    @Override
    public List<KnowledgeSource> sources() {
        Map<String, Integer> counts = new TreeMap<>();
        for (Chunk chunk : chunks) {
            if (chunk.source().isBlank()) {
                continue;
            }
            counts.merge(chunk.source(), 1, Integer::sum);
        }
        List<KnowledgeSource> out = new ArrayList<>(counts.size());
        counts.forEach((source, n) -> out.add(new KnowledgeSource(source, n)));
        return List.copyOf(out);
    }

    @Override
    public List<RetrieveHit> retrieve(String query, int topK) {
        return retrieve(query, topK, null);
    }

    @Override
    public List<RetrieveHit> retrieve(String query, int topK, String source) {
        if (query == null || query.isBlank() || topK <= 0 || chunks.isEmpty()) {
            return List.of();
        }
        String only = source == null ? "" : source.trim();
        Set<String> terms = terms(query);
        if (terms.isEmpty()) {
            return List.of();
        }
        record Scored(Chunk chunk, double score) {}
        List<Scored> scored = new ArrayList<>();
        for (Chunk chunk : chunks) {
            if (!only.isEmpty() && !only.equals(chunk.source())) {
                continue;
            }
            int matched = 0;
            String hay = chunk.text().toLowerCase(Locale.ROOT);
            for (String term : terms) {
                if (hay.contains(term)) {
                    matched++;
                }
            }
            if (matched <= 0) {
                continue;
            }
            double ratio = matched / (double) terms.size();
            if (ratio + 1e-9 >= minScore) {
                scored.add(new Scored(chunk, ratio));
            }
        }
        scored.sort(Comparator.comparingDouble(Scored::score).reversed());
        int limit = Math.min(topK, scored.size());
        List<RetrieveHit> hits = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            Chunk chunk = scored.get(i).chunk();
            hits.add(new RetrieveHit(chunk.id(), chunk.text(), scored.get(i).score(), chunk.source(), chunk.docType()));
        }
        return List.copyOf(hits);
    }

    static Set<String> terms(String query) {
        return QueryTerms.of(query);
    }

    private record Chunk(String id, String text, String source, String docType) {}
}

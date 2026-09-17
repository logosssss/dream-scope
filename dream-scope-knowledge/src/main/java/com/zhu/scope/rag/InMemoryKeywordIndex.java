package com.zhu.scope.rag;

import com.zhu.scope.knowledge.RetrieveHit;
import com.zhu.scope.knowledge.RetrievePort;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 进程内关键词检索。无向量库；入库后按词项重叠打分。
 */
public final class InMemoryKeywordIndex implements RetrievePort {

    private final CopyOnWriteArrayList<Chunk> chunks = new CopyOnWriteArrayList<>();

    private final AtomicInteger seq = new AtomicInteger();

    private final double minScore;

    public InMemoryKeywordIndex() {
        this(0.0);
    }

    public InMemoryKeywordIndex(double minScore) {
        if (minScore < 0.0 || minScore > 1.0) {
            throw new IllegalArgumentException("minScore must be in [0, 1]");
        }
        this.minScore = minScore;
    }

    public void ingest(String text, String source, String docType) {
        if (text == null || text.isBlank()) {
            return;
        }
        String src = source == null ? "" : source;
        String type = docType == null ? "" : docType.trim();
        chunks.add(new Chunk("kw-" + seq.incrementAndGet(), text.trim(), src, type));
    }

    @Override
    public List<RetrieveHit> retrieve(String query, int topK) {
        if (query == null || query.isBlank() || topK <= 0 || chunks.isEmpty()) {
            return List.of();
        }
        Set<String> terms = terms(query);
        if (terms.isEmpty()) {
            return List.of();
        }
        record Scored(Chunk chunk, double score) {}
        List<Scored> scored = new ArrayList<>();
        for (Chunk chunk : chunks) {
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
        Set<String> out = new LinkedHashSet<>();
        for (String raw : query.toLowerCase(Locale.ROOT).split("[\\s\\p{Punct}]+")) {
            if (raw.length() >= 2) {
                out.add(raw);
            }
        }
        return out;
    }

    private record Chunk(String id, String text, String source, String docType) {}
}

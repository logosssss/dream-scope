package com.zhu.scope.rag;

import com.zhu.scope.knowledge.IngestProgressListener;
import com.zhu.scope.knowledge.KnowledgeSource;
import com.zhu.scope.knowledge.QueryRewritePort;
import com.zhu.scope.knowledge.RerankPort;
import com.zhu.scope.knowledge.RetrieveHit;
import com.zhu.scope.knowledge.RetrievePort;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 在已有 {@link RetrievePort} 上叠加 query 改写与精排。改写/精排失败时退回内层结果。
 */
public final class AdvancedRetrievePort implements RetrievePort {

    private static final Logger log = LoggerFactory.getLogger(AdvancedRetrievePort.class);

    private final RetrievePort inner;

    private final QueryRewritePort rewriter;

    private final RerankPort reranker;

    private final int candidateMultiplier;

    public AdvancedRetrievePort(
            RetrievePort inner, QueryRewritePort rewriter, RerankPort reranker, int candidateMultiplier) {
        this.inner = inner;
        this.rewriter = rewriter;
        this.reranker = reranker;
        this.candidateMultiplier = Math.max(1, candidateMultiplier);
    }

    /** {@code min(topK * m, max(topK, 64))}：小 topK 仍放大，大 topK 不再被 64 截到比请求还少。 */
    static int candidateFetch(int topK, int multiplier) {
        if (topK <= 0) {
            return 0;
        }
        int m = Math.max(1, multiplier);
        return Math.min(topK * m, Math.max(topK, 64));
    }

    public AdvancedRetrievePort(RetrievePort inner, RerankPort reranker) {
        this(inner, null, reranker, 3);
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
        List<String> queries = expandQueries(query);
        int fetch = candidateFetch(topK, candidateMultiplier);
        log.info(
                "advanced retrieve start topK={} fetch={} source={} queries={} rewrite={} rerank={} chars={}",
                topK,
                fetch,
                source == null ? "" : source,
                queries.size(),
                rewriter != null,
                reranker != null,
                query.length());
        Map<String, RetrieveHit> merged = new LinkedHashMap<>();
        for (String q : queries) {
            List<RetrieveHit> hits = source == null || source.isBlank()
                    ? inner.retrieve(q, fetch)
                    : inner.retrieve(q, fetch, source);
            if (hits == null) {
                continue;
            }
            for (RetrieveHit hit : hits) {
                String id = hit.id() == null || hit.id().isBlank() ? hit.text() : hit.id();
                RetrieveHit old = merged.get(id);
                if (old == null || hit.score() > old.score()) {
                    merged.put(id, hit);
                }
            }
        }
        List<RetrieveHit> candidates = new ArrayList<>(merged.values());
        if (candidates.isEmpty()) {
            log.info("advanced retrieve miss topK={} queries={}", topK, queries.size());
            return List.of();
        }
        if (reranker == null) {
            List<RetrieveHit> out = trim(candidates, topK);
            log.info("advanced retrieve done mode=merge hits={} candidates={}", out.size(), candidates.size());
            return out;
        }
        try {
            List<RetrieveHit> ranked = reranker.rerank(query, candidates, topK);
            if (ranked == null || ranked.isEmpty()) {
                List<RetrieveHit> out = lexicalFallback(query, candidates, topK);
                log.warn("advanced rerank empty, lexical fallback hits={}", out.size());
                return out;
            }
            List<RetrieveHit> out = trim(ranked, topK);
            log.info(
                    "advanced retrieve done mode=rerank hits={} candidates={} topScore={}",
                    out.size(),
                    candidates.size(),
                    out.isEmpty() ? 0.0 : out.get(0).score());
            return out;
        } catch (RuntimeException ex) {
            List<RetrieveHit> out = lexicalFallback(query, candidates, topK);
            log.warn("advanced rerank failed, lexical fallback hits={}: {}", out.size(), ex.getMessage());
            return out;
        }
    }

    private static List<RetrieveHit> lexicalFallback(String query, List<RetrieveHit> candidates, int topK) {
        try {
            return new LexicalRerankPort().rerank(query, candidates, topK);
        } catch (RuntimeException ex) {
            return trim(candidates, topK);
        }
    }

    private List<String> expandQueries(String query) {
        if (rewriter == null) {
            return List.of(query);
        }
        try {
            List<String> expanded = rewriter.expand(query);
            if (expanded == null || expanded.isEmpty()) {
                log.info("advanced rewrite empty, use original chars={}", query.length());
                return List.of(query);
            }
            List<String> out = new ArrayList<>();
            out.add(query);
            for (String item : expanded) {
                if (item == null || item.isBlank()) {
                    continue;
                }
                String trimmed = item.trim();
                if (!out.contains(trimmed)) {
                    out.add(trimmed);
                }
            }
            log.info("advanced rewrite variants={} totalQueries={}", expanded.size(), out.size());
            return List.copyOf(out);
        } catch (RuntimeException ex) {
            log.warn("advanced rewrite failed, use original: {}", ex.getMessage());
            return List.of(query);
        }
    }

    private static List<RetrieveHit> trim(List<RetrieveHit> hits, int topK) {
        if (hits.size() <= topK) {
            return List.copyOf(hits);
        }
        return List.copyOf(hits.subList(0, topK));
    }

    @Override
    public List<KnowledgeSource> sources() {
        return inner.sources();
    }

    @Override
    public String addText(String id, String text, String source, String docType) {
        return inner.addText(id, text, source, docType);
    }

    @Override
    public List<String> addFile(String id, String filename, byte[] content, String source, String docType) {
        return inner.addFile(id, filename, content, source, docType);
    }

    @Override
    public int deleteBySource(String source) {
        return inner.deleteBySource(source);
    }

    @Override
    public void setIngestProgressListener(IngestProgressListener listener) {
        inner.setIngestProgressListener(listener);
    }

    @Override
    public void close() {
        inner.close();
    }
}

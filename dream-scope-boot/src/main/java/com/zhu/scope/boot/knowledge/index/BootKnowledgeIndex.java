package com.zhu.scope.boot.knowledge.index;

import com.zhu.scope.boot.knowledge.embed.BootEmbedder;
import com.zhu.scope.boot.knowledge.file.BootKnowledgeFiles;
import com.zhu.scope.boot.knowledge.store.BootPgKnowledge;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 进程内检索。先按词项重叠，有向量时再补上余弦相似度够高的条目。 */
public final class BootKnowledgeIndex {

    private static final Logger log = LoggerFactory.getLogger(BootKnowledgeIndex.class);

    private static final String[][] SEED = {
        {"dream-scope-boot 是 AgentScope 官方 Spring Boot starter 组成的独立进程，端口 8092。", "boot"},
        {"知识入口的 agentId 是 knowledge，只检索演示语料，不调用对话模型。", "knowledge"},
        {"演示 MCP 工具名是 mcp__boot__echo，监听 127.0.0.1:8094。", "mcp"}
    };

    private final BootEmbedder embedder;

    private final BootPgKnowledge pg;

    private final int chunkSize;

    private final int chunkOverlap;

    private final double minScore;

    private final List<Chunk> chunks = new ArrayList<>();

    public BootKnowledgeIndex(BootEmbedder embedder) {
        this(embedder, null, 2000, 200, 0.3);
    }

    public BootKnowledgeIndex(BootEmbedder embedder, BootPgKnowledge pg, int chunkSize, int chunkOverlap) {
        this(embedder, pg, chunkSize, chunkOverlap, 0.3);
    }

    public BootKnowledgeIndex(
            BootEmbedder embedder, BootPgKnowledge pg, int chunkSize, int chunkOverlap, double minScore) {
        this.embedder = embedder;
        this.pg = pg;
        this.chunkSize = chunkSize > 0 ? chunkSize : 2000;
        this.chunkOverlap = Math.max(0, chunkOverlap);
        this.minScore = minScore < 0 ? 0 : Math.min(minScore, 1);
    }

    public static BootKnowledgeIndex seeded(BootEmbedder embedder) {
        return seeded(embedder, null, 2000, 200, 0.3);
    }

    public static BootKnowledgeIndex seeded(
            BootEmbedder embedder, BootPgKnowledge pg, int chunkSize, int chunkOverlap) {
        return seeded(embedder, pg, chunkSize, chunkOverlap, 0.3);
    }

    public static BootKnowledgeIndex seeded(
            BootEmbedder embedder, BootPgKnowledge pg, int chunkSize, int chunkOverlap, double minScore) {
        BootKnowledgeIndex index = new BootKnowledgeIndex(embedder, pg, chunkSize, chunkOverlap, minScore);
        for (String[] row : SEED) {
            index.remember(row[1], row[0], row[1]);
        }
        return index;
    }

    public void close() {
        if (pg != null) {
            pg.close();
        }
    }

    public synchronized String add(String id, String text) {
        return add(id, text, "");
    }

    public synchronized String add(String id, String text, String source) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String docId = id == null || id.isBlank() ? "kw-" + UUID.randomUUID().toString().replace("-", "") : id.trim();
        String body = text.trim();
        String src = source == null ? "" : source.trim();
        if (pg != null) {
            pg.add(docId, body, src);
        }
        put(docId, body, src);
        return docId;
    }

    /** 启动示例只进内存。向量库只收接口写入，避免启动时百炼 500 打出 ERROR。 */
    private synchronized void remember(String id, String text, String source) {
        if (text == null || text.isBlank()) {
            return;
        }
        String docId = id == null || id.isBlank() ? "kw-" + UUID.randomUUID().toString().replace("-", "") : id.trim();
        put(docId, text.trim(), source == null ? "" : source.trim());
    }

    private void put(String docId, String body, String src) {
        Chunk next = new Chunk(docId, body, src, vectorOf(body));
        for (int i = 0; i < chunks.size(); i++) {
            if (docId.equals(chunks.get(i).id())) {
                chunks.set(i, next);
                return;
            }
        }
        chunks.add(next);
    }

    public synchronized List<String> addFile(String id, String filename, byte[] content) {
        List<String> parts = BootKnowledgeFiles.read(filename, content, chunkSize, chunkOverlap);
        if (parts.isEmpty()) {
            throw new IllegalArgumentException("file text required");
        }
        String base = id == null || id.isBlank() ? BootKnowledgeFiles.stemId(filename) : id.trim();
        List<String> ids = BootKnowledgeFiles.chunkIds(base, parts.size());
        List<String> written = new ArrayList<>(parts.size());
        String src = BootKnowledgeFiles.basename(filename);
        deleteBySource(src);
        for (int i = 0; i < parts.size(); i++) {
            written.add(add(ids.get(i), parts.get(i), src));
        }
        return List.copyOf(written);
    }

    public synchronized List<Source> sources() {
        Map<String, Integer> counts = new TreeMap<>();
        for (Chunk chunk : chunks) {
            if (chunk.source().isBlank()) {
                continue;
            }
            counts.merge(chunk.source(), 1, Integer::sum);
        }
        List<Source> out = new ArrayList<>(counts.size());
        counts.forEach((name, count) -> out.add(new Source(name, count)));
        return List.copyOf(out);
    }

    public synchronized int deleteBySource(String source) {
        String src = source == null ? "" : source.trim();
        if (src.isEmpty()) {
            return 0;
        }
        List<Chunk> kept = new ArrayList<>();
        int removed = 0;
        for (Chunk chunk : chunks) {
            if (src.equals(chunk.source())) {
                removed++;
                if (pg != null) {
                    pg.delete(chunk.id());
                }
            } else {
                kept.add(chunk);
            }
        }
        if (removed > 0) {
            chunks.clear();
            chunks.addAll(kept);
        }
        return removed;
    }

    public synchronized List<Hit> retrieve(String query, int topK) {
        return retrieve(query, topK, null);
    }

    public synchronized List<Hit> retrieve(String query, int topK, String source) {
        if (query == null || query.isBlank() || topK <= 0 || (chunks.isEmpty() && pg == null)) {
            return List.of();
        }
        String only = source == null ? "" : source.trim();
        Set<String> terms = terms(query);
        List<Scored> keyword = new ArrayList<>();
        for (Chunk chunk : chunks) {
            if (!only.isEmpty() && !only.equals(chunk.source())) {
                continue;
            }
            double score = overlap(terms, chunk.text());
            if (score > 0) {
                keyword.add(new Scored(chunk, score));
            }
        }
        keyword.sort(Comparator.comparingDouble(Scored::score).reversed());
        List<Hit> hits = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Scored row : keyword) {
            if (hits.size() >= topK) {
                break;
            }
            hits.add(row.chunk().hit(row.score()));
            seen.add(row.chunk().id());
        }
        if (hits.size() >= topK) {
            return List.copyOf(hits);
        }
        if (pg != null) {
            for (Hit hit : pg.search(query, topK, minScore)) {
                if (hits.size() >= topK || seen.contains(hit.id())) {
                    continue;
                }
                if (!only.isEmpty() && !only.equals(hit.source())) {
                    continue;
                }
                hits.add(hit);
                seen.add(hit.id());
            }
            return List.copyOf(hits);
        }
        if (embedder == null) {
            return List.copyOf(hits);
        }
        float[] queryVector = vectorOf(query);
        if (queryVector == null) {
            return List.copyOf(hits);
        }
        List<Scored> vectors = new ArrayList<>();
        for (Chunk chunk : chunks) {
            if (seen.contains(chunk.id()) || chunk.vector() == null) {
                continue;
            }
            if (!only.isEmpty() && !only.equals(chunk.source())) {
                continue;
            }
            double cosine = cosine(queryVector, chunk.vector());
            if (cosine + 1e-9 >= minScore) {
                vectors.add(new Scored(chunk, cosine));
            }
        }
        vectors.sort(Comparator.comparingDouble(Scored::score).reversed());
        for (Scored row : vectors) {
            if (hits.size() >= topK) {
                break;
            }
            hits.add(row.chunk().hit(row.score()));
        }
        return List.copyOf(hits);
    }

    private float[] vectorOf(String text) {
        if (embedder == null) {
            return null;
        }
        try {
            float[] vector = embedder.embed(text);
            return vector == null || vector.length == 0 ? null : vector;
        } catch (RuntimeException ex) {
            log.warn("embedding skipped: {}", ex.getMessage());
            return null;
        }
    }

    static double overlap(Set<String> terms, String text) {
        if (terms.isEmpty() || text == null) {
            return 0;
        }
        String hay = text.toLowerCase(Locale.ROOT);
        int matched = 0;
        for (String term : terms) {
            if (hay.contains(term)) {
                matched++;
            }
        }
        return matched / (double) terms.size();
    }

    static Set<String> terms(String query) {
        Set<String> out = new LinkedHashSet<>();
        if (query == null || query.isBlank()) {
            return out;
        }
        String lower = query.toLowerCase(Locale.ROOT);
        for (String raw : lower.split("[\\s\\p{Punct}]+")) {
            if (raw.length() >= 2 && !cjkOnly(raw)) {
                out.add(raw);
            }
        }
        int i = 0;
        while (i < lower.length()) {
            if (!isCjk(lower.charAt(i))) {
                i++;
                continue;
            }
            int j = i + 1;
            while (j < lower.length() && isCjk(lower.charAt(j))) {
                j++;
            }
            if (j - i == 1) {
                out.add(lower.substring(i, j));
            } else {
                for (int k = i; k + 1 < j; k++) {
                    out.add(lower.substring(k, k + 2));
                }
            }
            i = j;
        }
        return out;
    }

    static double cosine(float[] left, float[] right) {
        if (left == null || right == null || left.length == 0 || left.length != right.length) {
            return 0;
        }
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int i = 0; i < left.length; i++) {
            dot += left[i] * (double) right[i];
            leftNorm += left[i] * (double) left[i];
            rightNorm += right[i] * (double) right[i];
        }
        if (leftNorm == 0 || rightNorm == 0) {
            return 0;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private static boolean cjkOnly(String token) {
        for (int i = 0; i < token.length(); i++) {
            if (!isCjk(token.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isCjk(char c) {
        Character.UnicodeScript script = Character.UnicodeScript.of(c);
        return script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.HANGUL;
    }

    public record Hit(String id, String text, double score, String source) {
        public Hit(String id, String text, double score) {
            this(id, text, score, "");
        }

        public Hit {
            source = source == null ? "" : source;
        }
    }

    public record Source(String name, int count) {}

    private record Chunk(String id, String text, String source, float[] vector) {
        Hit hit(double score) {
            return new Hit(id, text, score, source);
        }
    }

    private record Scored(Chunk chunk, double score) {}
}

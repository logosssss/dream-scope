package com.zhu.scope.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zhu.scope.knowledge.EmbeddingIngestException;
import com.zhu.scope.knowledge.RetrieveHit;
import com.zhu.scope.knowledge.RetrievePort;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class HybridRetrievePortTest {

    @Test
    void addFileFallsBackToKeywordWithExtractedTexts() {
        RetrievePort primary = new RetrievePort() {
            @Override
            public List<RetrieveHit> retrieve(String query, int topK) {
                return List.of();
            }

            @Override
            public List<String> addFile(String id, String filename, byte[] content, String source, String docType) {
                throw new EmbeddingIngestException(
                        "simple knowledge ingest failed",
                        "guide",
                        "guide.pdf",
                        "file",
                        List.of("dream-scope Redis 会话", "AgentScope Harness"),
                        new RuntimeException("FreeTierOnly"));
            }
        };
        HybridRetrievePort hybrid = new HybridRetrievePort(primary);
        List<String> ids = hybrid.addFile(null, "guide.pdf", new byte[] {1, 2, 3}, "guide.pdf", "file");
        assertEquals(2, ids.size());
        assertFalse(hybrid.retrieve("Redis", 3).isEmpty());
        assertTrue(hybrid.retrieve("Harness", 3).stream().anyMatch(hit -> hit.text().contains("AgentScope")));
    }

    @Test
    void pdfEmbeddingFailureUsesPlainTextReader() {
        RetrievePort primary = new RetrievePort() {
            @Override
            public List<RetrieveHit> retrieve(String query, int topK) {
                return List.of();
            }

            @Override
            public List<String> addFile(String id, String filename, byte[] content, String source, String docType) {
                throw new IllegalStateException(
                        "simple knowledge ingest failed", new RuntimeException("FreeTierOnly"));
            }
        };
        HybridRetrievePort hybrid = new HybridRetrievePort(primary);
        hybrid.setFileChunkReader((name, content) -> List.of("PDF 正文里的 Redis 会话"));
        List<String> ids = hybrid.addFile(null, "guide.pdf", new byte[] {'%', 'P', 'D', 'F'}, "guide.pdf", "file");
        assertEquals(1, ids.size());
        assertFalse(hybrid.retrieve("Redis", 3).isEmpty());
    }

    @Test
    void addTextFallsBackOnEmbeddingQuota() {
        RetrievePort primary = new RetrievePort() {
            @Override
            public List<RetrieveHit> retrieve(String query, int topK) {
                return List.of();
            }

            @Override
            public String addText(String id, String text, String source, String docType) {
                throw new IllegalStateException(
                        "simple knowledge ingest failed", new RuntimeException("AllocationQuota.FreeTierOnly"));
            }
        };
        HybridRetrievePort hybrid = new HybridRetrievePort(primary);
        assertEquals("doc-1", hybrid.addText("doc-1", "dream-scope Redis 会话", "manual", "note"));
        assertFalse(hybrid.retrieve("Redis", 2).isEmpty());
    }

    @Test
    void nonEmbeddingFailurePropagates() {
        RetrievePort primary = new RetrievePort() {
            @Override
            public List<RetrieveHit> retrieve(String query, int topK) {
                return List.of();
            }

            @Override
            public List<String> addFile(String id, String filename, byte[] content, String source, String docType) {
                throw new IllegalArgumentException("unsupported");
            }
        };
        HybridRetrievePort hybrid = new HybridRetrievePort(primary);
        assertThrows(
                IllegalArgumentException.class,
                () -> hybrid.addFile(null, "x.bin", "hi".getBytes(StandardCharsets.UTF_8), "x.bin", "file"));
    }

    @Test
    void deleteBySourceClearsBothSides() {
        InMemoryKeywordIndex keyword = new InMemoryKeywordIndex();
        RetrievePort primary = new InMemoryKeywordIndex();
        HybridRetrievePort hybrid = new HybridRetrievePort(primary, keyword);
        hybrid.addText("a", "alpha Redis", "same.md", "note");
        keyword.addText("b", "beta Redis", "same.md", "note");
        assertEquals(2, hybrid.deleteBySource("same.md"));
        assertTrue(hybrid.retrieve("Redis", 3).isEmpty());
    }

    @Test
    void sourcesMergePrimaryAndKeyword() {
        InMemoryKeywordIndex keyword = new InMemoryKeywordIndex();
        InMemoryKeywordIndex primary = new InMemoryKeywordIndex();
        HybridRetrievePort hybrid = new HybridRetrievePort(primary, keyword);
        primary.addText("a", "alpha Redis", "book.pdf", "file");
        keyword.addText("b", "beta Redis", "notes.md", "file");
        assertEquals(2, hybrid.sources().size());
        assertEquals(1, hybrid.retrieve("Redis", 3, "notes.md").size());
    }

    @Test
    void mergesVectorAndKeywordHitsByRrf() {
        InMemoryKeywordIndex keyword = new InMemoryKeywordIndex();
        InMemoryKeywordIndex primary = new InMemoryKeywordIndex();
        HybridRetrievePort hybrid = new HybridRetrievePort(primary, keyword);
        primary.addText("vec-only", "向量库里的 Redis 会话说明", "vec.md", "note");
        keyword.addText("kw-only", "关键词里的 AgentGateway 专有名词", "kw.md", "note");
        List<RetrieveHit> hits = hybrid.retrieve("Redis AgentGateway", 5);
        assertEquals(2, hits.size());
        assertTrue(hits.stream().anyMatch(hit -> "vec-only".equals(hit.id())));
        assertTrue(hits.stream().anyMatch(hit -> "kw-only".equals(hit.id())));
    }
}

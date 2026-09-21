package com.zhu.scope.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zhu.scope.knowledge.RetrieveCitations;
import com.zhu.scope.knowledge.RetrieveHit;
import java.util.List;
import org.junit.jupiter.api.Test;

class InMemoryKeywordIndexTest {

    @Test
    void retrievesMatchingChunk() {
        InMemoryKeywordIndex index = new InMemoryKeywordIndex();
        index.ingest("HTTP 只进 AgentGateway。", "intro", "intro");
        List<RetrieveHit> hits = index.retrieve("AgentGateway", 3);
        assertEquals(1, hits.size());
        assertTrue(hits.get(0).text().contains("AgentGateway"));
        assertEquals("intro", hits.get(0).docType());
        assertTrue(RetrieveCitations.format(hits).startsWith("[1]"));
    }

    @Test
    void missesUnrelatedQuery() {
        InMemoryKeywordIndex index = new InMemoryKeywordIndex();
        index.ingest("HTTP 只进 AgentGateway。", "intro", "intro");
        assertTrue(index.retrieve("量子纠缠证明", 3).isEmpty());
    }

    @Test
    void addFileRejectsPdf() {
        InMemoryKeywordIndex index = new InMemoryKeywordIndex();
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> index.addFile(null, "scan.pdf", new byte[] {0x25, 0x50}, "scan.pdf", "file"));
        assertTrue(ex.getMessage().contains("text files"));
    }

    @Test
    void addTextKeepsStableId() {
        InMemoryKeywordIndex index = new InMemoryKeywordIndex();
        assertEquals("manual-1", index.addText("manual-1", "HTTP 只进 AgentGateway。", "intro", "note"));
        assertEquals("manual-1", index.retrieve("AgentGateway", 1).get(0).id());
        assertEquals("note", index.retrieve("AgentGateway", 1).get(0).docType());
    }

    @Test
    void retrieveAndSourcesStayOnOneFile() {
        InMemoryKeywordIndex index = new InMemoryKeywordIndex();
        index.addText("a", "Redis 会话在关键词里", "guide.pdf", "file");
        index.addText("b", "Redis 会话在另一本", "other.md", "file");
        assertEquals(1, index.retrieve("Redis", 5, "guide.pdf").size());
        assertEquals("guide.pdf", index.retrieve("Redis", 5, "guide.pdf").get(0).source());
        assertEquals(2, index.sources().size());
        assertEquals(1, index.deleteBySource("guide.pdf"));
        assertEquals(1, index.sources().size());
    }

    @Test
    void chineseBigramMatchesSplitTerms() {
        InMemoryKeywordIndex index = new InMemoryKeywordIndex();
        index.ingest("用户的会话已经写入存储层。", "intro", "intro");
        assertEquals(1, index.retrieve("会话存储", 3).size());
    }

    @Test
    void generatedIdsAreUniqueUuids() {
        InMemoryKeywordIndex index = new InMemoryKeywordIndex();
        String a = index.addText(null, "alpha Redis one", "s", "n");
        String b = index.addText(null, "beta Redis two", "s", "n");
        assertTrue(a.startsWith("kw-"));
        assertTrue(b.startsWith("kw-"));
        assertNotEquals(a, b);
        assertTrue(!a.substring(3).contains("-"));
    }
}

package com.zhu.scope.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}

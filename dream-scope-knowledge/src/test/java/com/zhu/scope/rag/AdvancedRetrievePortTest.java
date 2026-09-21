package com.zhu.scope.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zhu.scope.knowledge.RetrieveHit;
import com.zhu.scope.knowledge.RetrievePort;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdvancedRetrievePortTest {

    @Test
    void reranksCandidatesAndTrimsTopK() {
        RetrievePort inner = (query, topK) -> List.of(
                new RetrieveHit("weak", "无关内容", 0.9, "a", "note"),
                new RetrieveHit("strong", "dream-scope Redis 会话", 0.1, "b", "note"),
                new RetrieveHit("mid", "其它 Redis 说明", 0.5, "c", "note"));
        AdvancedRetrievePort port = new AdvancedRetrievePort(inner, new LexicalRerankPort());
        List<RetrieveHit> hits = port.retrieve("Redis 会话", 2);
        assertEquals(2, hits.size());
        assertEquals("strong", hits.get(0).id());
    }

    @Test
    void rewriteExpandsQueriesAndMergesHits() {
        List<String> seen = new ArrayList<>();
        RetrievePort inner = (query, topK) -> {
            seen.add(query);
            if (query.contains("AgentGateway")) {
                return List.of(new RetrieveHit("gw", "HTTP 只进 AgentGateway", 0.8, "http", "note"));
            }
            return List.of(new RetrieveHit("rt", "dream-scope 运行时", 0.7, "intro", "note"));
        };
        AdvancedRetrievePort port = new AdvancedRetrievePort(
                inner, q -> List.of(q, "AgentGateway HTTP"), null, 2);
        List<RetrieveHit> hits = port.retrieve("运行时入口", 5);
        assertTrue(seen.contains("运行时入口"));
        assertTrue(seen.contains("AgentGateway HTTP"));
        assertEquals(2, hits.size());
    }

    @Test
    void candidateFetchKeepsLargeTopK() {
        assertEquals(15, AdvancedRetrievePort.candidateFetch(5, 3));
        assertEquals(64, AdvancedRetrievePort.candidateFetch(30, 3));
        assertEquals(100, AdvancedRetrievePort.candidateFetch(100, 3));
    }
}

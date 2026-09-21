package com.zhu.scope.adapter.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zhu.scope.knowledge.RetrieveHit;
import java.util.List;
import org.junit.jupiter.api.Test;

class DashScopeRerankPortTest {

    @Test
    void parsesNestedOutputResults() {
        List<RetrieveHit> candidates = List.of(
                new RetrieveHit("a", "无关", 0.1, "s", "n"),
                new RetrieveHit("b", "Redis 会话", 0.2, "s", "n"));
        String json = "{\"output\":{\"results\":[{\"index\":1,\"relevance_score\":0.91},"
                + "{\"index\":0,\"relevance_score\":0.12}]}}";
        List<RetrieveHit> ranked = DashScopeRerankPort.parse(json, candidates, 1);
        assertEquals(1, ranked.size());
        assertEquals("b", ranked.get(0).id());
        assertEquals(0.91, ranked.get(0).score(), 1e-6);
    }

    @Test
    void buildBodyEscapesQuotes() {
        String body = DashScopeRerankPort.buildBody(
                "gte-rerank-v2",
                "say \"hi\"",
                List.of(new RetrieveHit("1", "line\n2", 0, "", "")),
                3);
        assertTrue(body.contains("say \\\"hi\\\""));
        assertTrue(body.contains("line\\n2"));
        assertTrue(body.contains("\"top_n\":3"));
    }
}

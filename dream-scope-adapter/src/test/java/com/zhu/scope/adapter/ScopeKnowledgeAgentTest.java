package com.zhu.scope.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zhu.scope.agent.AgentEvent;
import com.zhu.scope.agent.AgentIds;
import com.zhu.scope.agent.AgentInvokeRequest;
import com.zhu.scope.agent.AgentInvokeResult;
import com.zhu.scope.agent.AgentStreamHandler;
import com.zhu.scope.knowledge.RetrieveHit;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

class ScopeKnowledgeAgentTest {

    @Test
    void handleReturnsNumberedCitations() {
        ScopeKnowledgeAgent agent = new ScopeKnowledgeAgent((query, topK) -> {
            assertEquals(5, topK);
            return List.of(new RetrieveHit("kw-1", "dream-scope 是运行时", 1.0, "intro", "intro"));
        });
        AgentInvokeResult result =
                agent.handle(new AgentInvokeRequest(AgentIds.KNOWLEDGE, "s", "u", "dream-scope 是什么"));
        assertEquals(AgentIds.KNOWLEDGE, result.agentId());
        assertEquals("[1] dream-scope 是运行时", result.output());
    }

    @Test
    void handleMissReturnsEmptyHint() {
        ScopeKnowledgeAgent agent = new ScopeKnowledgeAgent((query, topK) -> List.of());
        AgentInvokeResult result = agent.handle(new AgentInvokeRequest(AgentIds.KNOWLEDGE, "s", "u", "无关"));
        assertEquals("未检索到相关资料", result.output());
    }

    @Test
    void streamEmitsTextThenDone() {
        ScopeKnowledgeAgent agent = new ScopeKnowledgeAgent(
                (query, topK) -> List.of(new RetrieveHit("kw-1", "正文", 1.0, "s", "")));
        List<AgentEvent> events = new CopyOnWriteArrayList<>();
        agent.streamHandle(new AgentInvokeRequest(AgentIds.KNOWLEDGE, "s", "u", "q"), new AgentStreamHandler() {
            @Override
            public void onEvent(AgentEvent event) {
                events.add(event);
            }

            @Override
            public void onComplete() {}

            @Override
            public void onError(Throwable error) {}
        });
        assertEquals("[1] 正文", ((AgentEvent.TextDelta) events.get(0)).text());
        assertEquals("[1] 正文", ((AgentEvent.Done) events.get(1)).finalOutput());
    }
}

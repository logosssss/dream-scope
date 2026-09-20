package com.zhu.scope.adapter;

import com.zhu.scope.agent.AgentEvent;
import com.zhu.scope.agent.AgentIds;
import com.zhu.scope.agent.AgentInvokeRequest;
import com.zhu.scope.agent.AgentInvokeResult;
import com.zhu.scope.agent.AgentStreamHandler;
import com.zhu.scope.agent.StreamingAgentHandler;
import com.zhu.scope.knowledge.RetrieveCitations;
import com.zhu.scope.knowledge.RetrievePort;
import java.util.Objects;

/**
 * 内置 {@code knowledge} Agent：只检索，不调模型。对外仍是 domain 类型。
 */
public final class ScopeKnowledgeAgent implements StreamingAgentHandler {

    static final int DEFAULT_TOP_K = 5;

    private static final String MISS = "未检索到相关资料";

    private final RetrievePort retrievePort;

    public ScopeKnowledgeAgent(RetrievePort retrievePort) {
        this.retrievePort = Objects.requireNonNull(retrievePort, "retrievePort");
    }

    @Override
    public String id() {
        return AgentIds.KNOWLEDGE;
    }

    @Override
    public AgentInvokeResult handle(AgentInvokeRequest request) {
        String query = request == null ? "" : request.input();
        String formatted = RetrieveCitations.format(retrievePort.retrieve(query, DEFAULT_TOP_K));
        String output = formatted.isEmpty() ? MISS : formatted;
        return new AgentInvokeResult(id(), output);
    }

    @Override
    public void streamHandle(AgentInvokeRequest request, AgentStreamHandler handler) {
        Objects.requireNonNull(handler, "handler");
        AgentInvokeResult result = handle(request);
        handler.onEvent(new AgentEvent.TextDelta(result.output()));
        handler.onEvent(new AgentEvent.Done(result.output()));
        handler.onComplete();
    }
}

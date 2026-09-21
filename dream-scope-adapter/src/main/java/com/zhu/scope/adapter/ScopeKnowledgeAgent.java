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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 内置 {@code knowledge} Agent：只检索，不调模型。对外仍是 domain 类型。
 */
public final class ScopeKnowledgeAgent implements StreamingAgentHandler {

    private static final Logger log = LoggerFactory.getLogger(ScopeKnowledgeAgent.class);

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
        var hits = retrievePort.retrieve(query, DEFAULT_TOP_K);
        String formatted = RetrieveCitations.format(hits);
        String output = formatted.isEmpty() ? MISS : formatted;
        log.info(
                "knowledge retrieve hits={} queryChars={} preview={}",
                hits.size(),
                query.length(),
                LogText.preview(query, 80));
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

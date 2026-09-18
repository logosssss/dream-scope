package com.zhu.scope.agent;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 一次调用的出参。不携带 AgentScope Event / Message。
 */
public record AgentInvokeResult(
        String agentId,
        String output,
        int inputTokens,
        int outputTokens,
        Map<String, Object> data,
        boolean planActive) {

    public AgentInvokeResult(String agentId, String output) {
        this(agentId, output, 0, 0, null, false);
    }

    public AgentInvokeResult(String agentId, String output, int inputTokens, int outputTokens) {
        this(agentId, output, inputTokens, outputTokens, null, false);
    }

    public AgentInvokeResult(String agentId, String output, int inputTokens, int outputTokens, Map<String, Object> data) {
        this(agentId, output, inputTokens, outputTokens, data, false);
    }

    public AgentInvokeResult {
        agentId = agentId == null ? "" : agentId;
        output = output == null ? "" : output;
        if (inputTokens < 0) {
            inputTokens = 0;
        }
        if (outputTokens < 0) {
            outputTokens = 0;
        }
        data = copyData(data);
    }

    static Map<String, Object> copyData(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(data));
    }
}

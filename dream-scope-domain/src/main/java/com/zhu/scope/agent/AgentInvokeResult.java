package com.zhu.scope.agent;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一次调用的出参。不携带 AgentScope Event / Message。
 *
 * <p>{@code trace} 是同步调用收下来的链路：工具入参/出参、计划提示、流上的 error。不含 textDelta。
 */
public record AgentInvokeResult(
        String agentId,
        String output,
        int inputTokens,
        int outputTokens,
        Map<String, Object> data,
        boolean planActive,
        List<AgentTraceStep> trace) {

    public AgentInvokeResult(String agentId, String output) {
        this(agentId, output, 0, 0, null, false, null);
    }

    public AgentInvokeResult(String agentId, String output, int inputTokens, int outputTokens) {
        this(agentId, output, inputTokens, outputTokens, null, false, null);
    }

    public AgentInvokeResult(String agentId, String output, int inputTokens, int outputTokens, Map<String, Object> data) {
        this(agentId, output, inputTokens, outputTokens, data, false, null);
    }

    public AgentInvokeResult(
            String agentId,
            String output,
            int inputTokens,
            int outputTokens,
            Map<String, Object> data,
            boolean planActive) {
        this(agentId, output, inputTokens, outputTokens, data, planActive, null);
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
        trace = trace == null || trace.isEmpty() ? List.of() : List.copyOf(trace);
    }

    public static Map<String, Object> copyData(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(data));
    }
}

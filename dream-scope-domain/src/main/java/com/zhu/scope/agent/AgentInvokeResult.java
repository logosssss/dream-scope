package com.zhu.scope.agent;

/**
 * 一次调用的出参。不携带 AgentScope Event / Message。
 */
public record AgentInvokeResult(String agentId, String output, int inputTokens, int outputTokens) {

    public AgentInvokeResult(String agentId, String output) {
        this(agentId, output, 0, 0);
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
    }
}

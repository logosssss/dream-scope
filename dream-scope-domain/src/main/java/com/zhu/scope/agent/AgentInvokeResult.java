package com.zhu.scope.agent;

/**
 * 一次调用的出参。不携带 AgentScope Event / Message。
 */
public record AgentInvokeResult(String agentId, String output) {

    public AgentInvokeResult {
        agentId = agentId == null ? "" : agentId;
        output = output == null ? "" : output;
    }
}

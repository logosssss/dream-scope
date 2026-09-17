package com.zhu.scope.agent;

/**
 * 一次调用的入参。HTTP / SSE 在 web 转成本类型，再进 Handler。
 */
public record AgentInvokeRequest(String agentId, String sessionId, String userId, String input) {

    public AgentInvokeRequest {
        agentId = agentId == null || agentId.isBlank() ? AgentIds.CHAT : agentId.trim();
        sessionId = sessionId == null ? "" : sessionId.trim();
        userId = userId == null ? "" : userId.trim();
        input = input == null ? "" : input;
    }

    public boolean hasInput() {
        return !input.isBlank();
    }
}

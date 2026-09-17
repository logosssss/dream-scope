package com.zhu.scope.web;

/**
 * HTTP 入参。转成 domain {@code AgentInvokeRequest} 后再进 Handler。
 */
public record AgentInvokeHttpRequest(String agentId, String sessionId, String userId, String input) {}

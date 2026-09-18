package com.zhu.scope.web;

public record AgentInvokeHttpResponse(String agentId, String output, int inputTokens, int outputTokens) {}

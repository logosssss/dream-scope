package com.zhu.scope.web;

import java.util.Map;

public record AgentInvokeHttpResponse(
        String agentId,
        String output,
        int inputTokens,
        int outputTokens,
        Map<String, Object> data,
        boolean planActive) {}

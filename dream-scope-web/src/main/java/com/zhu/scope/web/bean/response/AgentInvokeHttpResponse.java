package com.zhu.scope.web.bean.response;

import java.util.Map;

public record AgentInvokeHttpResponse(
        String agentId,
        String output,
        int inputTokens,
        int outputTokens,
        Map<String, Object> data,
        boolean planActive) {}

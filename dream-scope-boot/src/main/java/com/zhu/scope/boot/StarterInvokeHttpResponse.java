package com.zhu.scope.boot;

import java.util.Map;

record StarterInvokeHttpResponse(
        String agentId,
        String output,
        int inputTokens,
        int outputTokens,
        Map<String, Object> data,
        boolean planActive) {}

package com.zhu.scope.boot;

import java.util.List;
import java.util.Map;

record StarterInvokeHttpRequest(
        String agentId,
        String sessionId,
        String userId,
        String input,
        List<String> imageUrls,
        Boolean structured,
        Map<String, Object> jsonSchema) {}

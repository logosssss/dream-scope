package com.zhu.scope.boot.http.bean.request;

import java.util.List;
import java.util.Map;

public record StarterInvokeHttpRequest(
        String agentId,
        String sessionId,
        String userId,
        String input,
        List<String> imageUrls,
        Boolean structured,
        Map<String, Object> jsonSchema) {}

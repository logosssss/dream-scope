package com.zhu.scope.web.bean.request;

import java.util.List;
import java.util.Map;

/**
 * HTTP 入参。转成 domain {@code AgentInvokeRequest} 后再进 Handler。
 */
public record AgentInvokeHttpRequest(
        String agentId,
        String sessionId,
        String userId,
        String input,
        List<String> imageUrls,
        Boolean structured,
        Map<String, Object> jsonSchema) {}

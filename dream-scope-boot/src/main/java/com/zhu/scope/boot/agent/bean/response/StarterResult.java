package com.zhu.scope.boot.agent.bean.response;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** boot 同步调用的出参。 */
public record StarterResult(
        String agentId,
        String output,
        int inputTokens,
        int outputTokens,
        Map<String, Object> data,
        boolean planActive) {

    public StarterResult(String agentId, String output) {
        this(agentId, output, 0, 0, null, false);
    }

    public StarterResult {
        agentId = agentId == null ? "" : agentId;
        output = output == null ? "" : output;
        if (inputTokens < 0) {
            inputTokens = 0;
        }
        if (outputTokens < 0) {
            outputTokens = 0;
        }
        data = copyData(data);
    }

    public static Map<String, Object> copyData(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(data));
    }
}

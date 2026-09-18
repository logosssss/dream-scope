package com.zhu.scope.adapter;

import java.nio.file.Path;
import java.time.Duration;

/**
 * chat Harness 装配参数。由 web 从 {@code dream-scope.*} 填入，本模块再转成 AgentScope 配置。
 * 生产会话走 Redis（必选），不再用 JSON 文件 store。
 */
public record ChatHarnessOptions(
        Duration timeout,
        Path workspace,
        int compactionTriggerMessages,
        int compactionKeepMessages,
        String redisUri,
        String redisKeyPrefix,
        Double temperature,
        Double topP,
        Integer maxTokens,
        String fallbackModelId,
        String fallbackApiKey,
        Boolean planModeEnabled,
        String planDirectory) {

    public static final int DEFAULT_TRIGGER_MESSAGES = 30;

    public static final int DEFAULT_KEEP_MESSAGES = 10;

    public ChatHarnessOptions {
        timeout = timeout == null || timeout.isZero() || timeout.isNegative()
                ? ScopeChatAgent.DEFAULT_TIMEOUT
                : timeout;
        compactionTriggerMessages =
                compactionTriggerMessages > 0 ? compactionTriggerMessages : DEFAULT_TRIGGER_MESSAGES;
        compactionKeepMessages =
                compactionKeepMessages > 0 ? compactionKeepMessages : DEFAULT_KEEP_MESSAGES;
        redisUri = redisUri == null || redisUri.isBlank() ? ChatRedis.DEFAULT_URI : redisUri.trim();
        redisKeyPrefix =
                redisKeyPrefix == null || redisKeyPrefix.isBlank()
                        ? ChatRedis.DEFAULT_KEY_PREFIX
                        : redisKeyPrefix.trim();
        if (temperature != null && (temperature < 0.0 || temperature > 2.0)) {
            throw new IllegalArgumentException("temperature must be in [0, 2]");
        }
        if (topP != null && (topP <= 0.0 || topP > 1.0)) {
            throw new IllegalArgumentException("topP must be in (0, 1]");
        }
        if (maxTokens != null && maxTokens <= 0) {
            throw new IllegalArgumentException("maxTokens must be positive");
        }
        fallbackModelId = blankToNull(fallbackModelId);
        fallbackApiKey = blankToNull(fallbackApiKey);
        planModeEnabled = planModeEnabled == null || planModeEnabled;
        planDirectory = planDirectory == null || planDirectory.isBlank() ? "plans" : planDirectory.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

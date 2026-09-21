package com.zhu.scope.adapter.nacos;

import java.util.List;
import java.util.Map;

/**
 * Nacos 连接与功能开关。web 从 {@code dream-scope.nacos.*} 填入，不含 {@code io.agentscope}。
 *
 * <p>总开关在调用方：没开 {@code dream-scope.nacos.enabled} 时不要 {@link ChatNacosClient#open}。
 */
public record ChatNacosSettings(
        String serverAddr,
        String namespace,
        String username,
        String password,
        boolean promptEnabled,
        String sysPromptKey,
        String promptVersion,
        String promptLabel,
        Map<String, String> promptVariables,
        boolean a2aRegistryEnabled,
        boolean a2aDiscoveryEnabled,
        String a2aDiscoveryAgentName,
        boolean registerAsLatest,
        boolean registerEndpoint,
        boolean a2aStreaming,
        boolean skillEnabled,
        List<String> skillNames,
        String skillVersion,
        String skillLabel) {

    public ChatNacosSettings {
        serverAddr = blankToDefault(serverAddr, "127.0.0.1:8848");
        namespace = blankToDefault(namespace, "public");
        username = blankToNull(username);
        password = blankToNull(password);
        sysPromptKey = blankToNull(sysPromptKey);
        promptVersion = blankToNull(promptVersion);
        promptLabel = blankToNull(promptLabel);
        promptVariables = promptVariables == null || promptVariables.isEmpty() ? Map.of() : Map.copyOf(promptVariables);
        a2aDiscoveryAgentName = blankToNull(a2aDiscoveryAgentName);
        skillNames = skillNames == null || skillNames.isEmpty() ? List.of() : List.copyOf(skillNames);
        skillVersion = blankToNull(skillVersion);
        skillLabel = blankToNull(skillLabel);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String blankToDefault(String value, String fallback) {
        String trimmed = blankToNull(value);
        return trimmed == null ? fallback : trimmed;
    }
}

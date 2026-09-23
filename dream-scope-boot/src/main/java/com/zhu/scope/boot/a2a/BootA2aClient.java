package com.zhu.scope.boot.a2a;

import io.agentscope.core.a2a.agent.A2aAgent;
import io.agentscope.core.a2a.agent.card.WellKnownAgentCardResolver;
import org.springframework.core.env.Environment;

/** 调用别的进程上的 A2A Agent。默认关闭，构建时不访问远端。 */
public final class BootA2aClient {

    private BootA2aClient() {}

    public static boolean enabled(Environment env) {
        return Boolean.TRUE.equals(env.getProperty("agentscope.a2a.client.enabled", Boolean.class, false));
    }

    public static String url(Environment env) {
        String value = env.getProperty("agentscope.a2a.client.url", "");
        return value == null ? "" : value.trim();
    }

    public static String name(Environment env) {
        String value = env.getProperty("agentscope.a2a.client.name", "remote");
        return value == null || value.isBlank() ? "remote" : value.trim();
    }

    public static A2aAgent open(Environment env) {
        if (!enabled(env)) {
            return null;
        }
        return open(url(env), name(env));
    }

    public static A2aAgent open(String url, String name) {
        String base = url == null || url.isBlank() ? "http://127.0.0.1:8092" : url.trim();
        String agentName = name == null || name.isBlank() ? "remote" : name.trim();
        return A2aAgent.builder()
                .name(agentName)
                .agentCardResolver(WellKnownAgentCardResolver.builder().baseUrl(base).build())
                .build();
    }
}

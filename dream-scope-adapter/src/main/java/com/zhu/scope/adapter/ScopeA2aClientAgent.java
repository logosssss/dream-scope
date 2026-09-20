package com.zhu.scope.adapter;

import com.zhu.scope.adapter.nacos.ChatNacosClient;
import com.zhu.scope.agent.AgentHandler;
import com.zhu.scope.agent.AgentIds;
import com.zhu.scope.agent.AgentInvokeException;
import com.zhu.scope.agent.AgentInvokeRequest;
import com.zhu.scope.agent.AgentInvokeResult;
import com.zhu.scope.agent.AgentProviderException;
import io.a2a.client.config.ClientConfig;
import io.agentscope.core.a2a.agent.A2aAgent;
import io.agentscope.core.a2a.agent.A2aAgentConfig;
import io.agentscope.core.a2a.agent.card.WellKnownAgentCardResolver;
import io.agentscope.core.message.Msg;
import java.time.Duration;
import java.util.Objects;

/**
 * 官方 {@code agentscope-extensions-a2a-client}：{@link A2aAgent} + {@link WellKnownAgentCardResolver}
 * 把远端 A2A Agent 包装成本地 {@code agentId=a2a} Handler。
 */
public final class ScopeA2aClientAgent implements AgentHandler {

    private final A2aAgent remote;

    private final Duration timeout;

    public ScopeA2aClientAgent(String remoteUrl) {
        this(buildRemote(remoteUrl), Duration.ofSeconds(30));
    }

    public static ScopeA2aClientAgent fromNacos(ChatNacosClient client, String agentName) {
        Objects.requireNonNull(client, "nacos");
        return new ScopeA2aClientAgent(client.discover(agentName), Duration.ofSeconds(30));
    }

    ScopeA2aClientAgent(A2aAgent remote, Duration timeout) {
        this.remote = Objects.requireNonNull(remote, "remote");
        this.timeout = timeout == null ? Duration.ofSeconds(30) : timeout;
    }

    @Override
    public String id() {
        return AgentIds.A2A;
    }

    @Override
    public AgentInvokeResult handle(AgentInvokeRequest request) {
        String input = request == null ? "" : request.input();
        try {
            Msg result = remote.call(input).block(timeout);
            return new AgentInvokeResult(id(), MessageCodec.textOf(result));
        } catch (AgentInvokeException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new AgentProviderException("a2a remote call failed", ex);
        }
    }

    static A2aAgent buildRemote(String remoteUrl) {
        String base = normalizeBase(remoteUrl);
        WellKnownAgentCardResolver resolver = WellKnownAgentCardResolver.builder()
                .baseUrl(base)
                .relativeCardPath("/.well-known/agent-card.json")
                .build();
        A2aAgentConfig config = A2aAgentConfig.builder()
                .clientConfig(ClientConfig.builder().setStreaming(false).build())
                .build();
        return A2aAgent.builder()
                .name(AgentIds.A2A)
                .agentCardResolver(resolver)
                .a2aAgentConfig(config)
                .build();
    }

    static String normalizeBase(String remoteUrl) {
        if (remoteUrl == null || remoteUrl.isBlank()) {
            throw new IllegalArgumentException("a2a remote url required");
        }
        String trimmed = remoteUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.endsWith("/a2a")) {
            return trimmed.substring(0, trimmed.length() - 4);
        }
        return trimmed;
    }
}

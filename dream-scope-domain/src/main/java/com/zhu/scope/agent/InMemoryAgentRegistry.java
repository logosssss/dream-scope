package com.zhu.scope.agent;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内注册表。无 Spring、无 AgentScope，单测可直接 new。
 */
public final class InMemoryAgentRegistry implements AgentRegistry {

    private final Map<String, AgentHandler> handlers = new ConcurrentHashMap<>();

    public InMemoryAgentRegistry(List<AgentHandler> agents) {
        if (agents == null) {
            return;
        }
        for (AgentHandler handler : agents) {
            register(handler);
        }
    }

    public void register(AgentHandler handler) {
        if (handler == null || handler.id() == null || handler.id().isBlank()) {
            throw new IllegalArgumentException("agent handler id required");
        }
        handlers.put(handler.id(), handler);
    }

    @Override
    public Optional<AgentHandler> find(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(handlers.get(agentId));
    }
}

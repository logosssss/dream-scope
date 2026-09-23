package com.zhu.scope.boot.agent;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** boot 进程内按 id 查找 Handler。 */
public final class StarterHandlerRegistry {

    private final Map<String, StarterHandler> handlers = new ConcurrentHashMap<>();

    public StarterHandlerRegistry(List<StarterHandler> agents) {
        if (agents == null) {
            return;
        }
        for (StarterHandler handler : agents) {
            if (handler == null || handler.id() == null || handler.id().isBlank()) {
                throw new IllegalArgumentException("agent handler id required");
            }
            handlers.put(handler.id(), handler);
        }
    }

    public Optional<StarterHandler> find(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(handlers.get(agentId));
    }
}
